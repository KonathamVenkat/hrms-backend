package com.hrms.recruitment.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.auth.service.AuditTrail;
import com.hrms.common.dto.PagedResponse;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.util.Strings;
import com.hrms.recruitment.dto.request.ApproveRequisitionRequest;
import com.hrms.recruitment.dto.request.RejectRequisitionRequest;
import com.hrms.recruitment.dto.request.RequisitionActionRequest;
import com.hrms.recruitment.dto.request.RequisitionRequest;
import com.hrms.recruitment.dto.response.ApprovalResponse;
import com.hrms.recruitment.dto.response.PersonOptionResponse;
import com.hrms.recruitment.dto.response.RaiseOptionsResponse;
import com.hrms.recruitment.dto.response.RequisitionResponse;
import com.hrms.recruitment.dto.response.RequisitionSummaryResponse;
import com.hrms.recruitment.entity.JobRequisition;
import com.hrms.recruitment.entity.RequisitionApproval;
import com.hrms.recruitment.enums.ApprovalDecision;
import com.hrms.recruitment.enums.RequisitionStatus;
import com.hrms.recruitment.enums.RequisitionType;
import com.hrms.recruitment.repository.JobRequisitionRepository;
import com.hrms.recruitment.repository.RequisitionApprovalRepository;
import com.hrms.recruitment.service.RecruitmentOrgLookup;
import com.hrms.recruitment.service.RequisitionService;
import com.hrms.recruitment.service.RequisitionSpecs;
import com.hrms.recruitment.workflow.RequisitionWorkflow;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.hrms.recruitment.enums.RequisitionStatus.*;

/**
 * Who may do what (the controller only checks the role; the data-level rules are here):
 * <ul>
 *   <li>raise, edit (DRAFT only) and submit: HR, or the employee who heads the requisition's department;</li>
 *   <li>approve, reject, hold, resume, close: HR only, and nobody approves or rejects their own requisition;</li>
 *   <li>cancel: HR, or the requester while it is still DRAFT or PENDING_APPROVAL;</li>
 *   <li>read: HR, the requester, or the head of the department.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequisitionServiceImpl implements RequisitionService {

    private static final String TARGET = "REQUISITION";
    private static final int MAX_PAGE_SIZE = 100;
    /** Same currency payroll uses (EmployeeSalary); move both to one setting when a second currency is needed. */
    private static final String DEFAULT_CURRENCY = "SSP";
    /** Allow-list of sortable properties: the API name on the left, the entity property on the right. */
    private static final Map<String, String> SORTABLE = Map.of(
        "createdAt", "createdAt", "code", "code", "status", "status",
        "jobTitle", "jobTitle", "targetJoinDate", "targetJoinDate");

    private final JobRequisitionRepository requisitions;
    private final RequisitionApprovalRepository approvals;
    private final RecruitmentOrgLookup lookup;
    private final RequisitionWorkflow workflow;
    private final EmployeeAccessGuard guard;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    // ----------------------------------------------------------------------------------------- reads

    @Override
    public RequisitionResponse get(Long id) {
        JobRequisition r = find(id);
        assertMayView(r);
        return toResponse(r);
    }

    @Override
    public PagedResponse<RequisitionSummaryResponse> search(RequisitionStatus status, Long departmentId, String q,
                                                            int page, int size, String sortBy, String direction) {
        Specification<JobRequisition> spec = RequisitionSpecs.matching(status, departmentId, q);
        if (!guard.isHrStaff()) {
            Long me = requireCaller();
            spec = spec.and(RequisitionSpecs.visibleTo(me, lookup.departmentIdsHeadedBy(me)));
        }
        Page<JobRequisition> result = requisitions.findAll(spec, pageableOf(page, size, sortBy, direction));
        return PagedResponse.from(toSummaries(result));
    }

    @Override
    public RaiseOptionsResponse raiseOptions() {
        var departments = lookup.departmentsRaisableBy(guard.currentEmployeeId(), guard.isHrStaff());
        Set<Long> ids = new HashSet<>();
        departments.forEach(d -> ids.add(d.getDeptId()));
        return new RaiseOptionsResponse(
            departments.stream().map(d -> new RaiseOptionsResponse.Option(d.getDeptId(), d.getName(), d.getNameAr())).toList(),
            // A designation with no department fits any department; the others only their own.
            lookup.activeDesignations().stream()
                .filter(d -> d.getDepartmentId() == null || ids.contains(d.getDepartmentId()))
                .map(d -> new RaiseOptionsResponse.DesignationOption(d.getDesigId(), d.getTitle(), d.getTitleAr(), d.getDepartmentId()))
                .toList(),
            lookup.activeLocations().stream()
                .map(l -> new RaiseOptionsResponse.Option(l.getLocationId(), l.getLocationName(), l.getLocationNameAr()))
                .toList());
    }

    @Override
    public List<PersonOptionResponse> peopleOf(Long departmentId) {
        assertMayRaise(departmentId, requireCaller());
        return lookup.activeEmployeesOf(departmentId).stream()
            .map(e -> new PersonOptionResponse(e.getId(), e.getEmployeeCode(), e.getFullName(), e.getFullNameAr()))
            .toList();
    }

    // ---------------------------------------------------------------------------------------- writes

    @Override
    @Transactional
    public RequisitionResponse create(RequisitionRequest request) {
        Long me = requireCaller();
        assertMayRaise(request.departmentId(), me);
        validate(request);

        JobRequisition r = JobRequisition.builder()
            .code(nextCode())
            .requestedBy(me)
            .filledPositions(0)
            .currency(DEFAULT_CURRENCY)
            .status(DRAFT)
            .build();
        apply(r, request);
        JobRequisition saved = requisitions.save(r);
        entityManager.flush();
        log.info("Requisition created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("REQUISITION_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public RequisitionResponse update(Long id, RequisitionRequest request) {
        JobRequisition r = find(id);
        Long me = requireCaller();
        assertRequesterOrHr(r, me);
        if (request.version() == null) throw new BadRequestException("version is required to update a requisition");
        requireCurrent(request.version(), r.getVersion());
        if (r.getStatus() != DRAFT) {
            throw new BusinessRuleException("REQUISITION_NOT_EDITABLE",
                "Only a draft requisition can be edited; " + r.getCode() + " is " + r.getStatus() + ".");
        }
        assertMayRaise(request.departmentId(), me);
        validate(request);

        String before = summary(r);
        apply(r, request);
        JobRequisition saved = requisitions.save(r);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("REQUISITION_UPDATED", TARGET, id, before + " -> " + summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public RequisitionResponse submit(Long id, RequisitionActionRequest request) {
        JobRequisition r = find(id);
        assertRequesterOrHr(r, requireCaller());
        requireCurrent(request.version(), r.getVersion());
        workflow.assertCanMove(r.getStatus(), PENDING_APPROVAL);
        // The department, designation or manager may have been deactivated since the draft was saved.
        lookup.activeDepartment(r.getDepartmentId());
        lookup.activeDesignation(r.getDesignationId(), r.getDepartmentId());
        return move(r, PENDING_APPROVAL, "REQUISITION_SUBMITTED");
    }

    @Override
    @Transactional
    public RequisitionResponse approve(Long id, ApproveRequisitionRequest request) {
        JobRequisition r = prepareDecision(id, request.version());
        Long me = requireCaller();
        assertNotOwn(r, me);
        workflow.assertCanMove(r.getStatus(), APPROVED);
        approvals.save(decision(r, me, ApprovalDecision.APPROVED, request.comments(), true, true));
        return move(r, APPROVED, "REQUISITION_APPROVED");
    }

    @Override
    @Transactional
    public RequisitionResponse reject(Long id, RejectRequisitionRequest request) {
        JobRequisition r = prepareDecision(id, request.version());
        Long me = requireCaller();
        assertNotOwn(r, me);
        workflow.assertCanMove(r.getStatus(), REJECTED);
        approvals.save(decision(r, me, ApprovalDecision.REJECTED, request.reason(), false, false));
        return move(r, REJECTED, "REQUISITION_REJECTED");
    }

    @Override
    @Transactional
    public RequisitionResponse hold(Long id, RequisitionActionRequest request) {
        return hrMove(id, request, ON_HOLD, "REQUISITION_HELD");
    }

    @Override
    @Transactional
    public RequisitionResponse resume(Long id, RequisitionActionRequest request) {
        return hrMove(id, request, APPROVED, "REQUISITION_RESUMED");
    }

    @Override
    @Transactional
    public RequisitionResponse close(Long id, RequisitionActionRequest request) {
        return hrMove(id, request, CLOSED, "REQUISITION_CLOSED");
    }

    @Override
    @Transactional
    public RequisitionResponse cancel(Long id, RequisitionActionRequest request) {
        JobRequisition r = find(id);
        Long me = requireCaller();
        boolean ownAndEarly = Objects.equals(r.getRequestedBy(), me) && (r.getStatus() == DRAFT || r.getStatus() == PENDING_APPROVAL);
        if (!guard.isHrStaff() && !ownAndEarly) {
            throw new AccessDeniedException("You can only cancel your own requisition before it is approved.");
        }
        requireCurrent(request.version(), r.getVersion());
        workflow.assertCanMove(r.getStatus(), CANCELLED);
        return move(r, CANCELLED, "REQUISITION_CANCELLED");
    }

    // -------------------------------------------------------------------------------------- helpers

    private RequisitionResponse hrMove(Long id, RequisitionActionRequest request, RequisitionStatus to, String action) {
        JobRequisition r = prepareDecision(id, request.version());
        workflow.assertCanMove(r.getStatus(), to);
        return move(r, to, action);
    }

    /** Loads the requisition for an HR-only action and checks the caller and the version, before any state rule. */
    private JobRequisition prepareDecision(Long id, Long version) {
        JobRequisition r = find(id);
        if (!guard.isHrStaff()) throw new AccessDeniedException("Only HR can do this.");
        requireCurrent(version, r.getVersion());
        return r;
    }

    private RequisitionResponse move(JobRequisition r, RequisitionStatus to, String action) {
        RequisitionStatus from = r.getStatus();
        r.setStatus(to);
        if (to == CLOSED || to == CANCELLED) r.setClosedAt(LocalDateTime.now());
        JobRequisition saved = requisitions.save(r);
        entityManager.flush(); // surfaces a lost race here as 409 and returns the new version
        audit.record(action, TARGET, saved.getId(), "code=" + saved.getCode() + ", " + from + " -> " + to);
        return toResponse(saved);
    }

    private RequisitionApproval decision(JobRequisition r, Long approver, ApprovalDecision decision, String comments,
                                         boolean budgetConfirmed, boolean managementConfirmed) {
        return RequisitionApproval.builder()
            .requisitionId(r.getId())
            .levelNo(RequisitionApproval.FIRST_LEVEL)
            .approverId(approver)
            .decision(decision)
            .budgetConfirmed(budgetConfirmed)
            .managementConfirmed(managementConfirmed)
            .comments(Strings.trimToNull(comments))
            .actionAt(LocalDateTime.now())
            .build();
    }

    private JobRequisition find(Long id) {
        return requisitions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Requisition", "id", id));
    }

    private Long requireCaller() {
        Long me = guard.currentEmployeeId();
        if (me == null) {
            throw new BusinessRuleException("NO_EMPLOYEE_PROFILE", "Your account is not linked to an employee record.");
        }
        return me;
    }

    private void assertMayRaise(Long departmentId, Long me) {
        if (!guard.isHrStaff() && !lookup.isDepartmentHead(departmentId, me)) {
            throw new AccessDeniedException("Only HR or the head of the department can raise a requisition for it.");
        }
    }

    private void assertMayView(JobRequisition r) {
        if (guard.isHrStaff()) return;
        Long me = requireCaller();
        if (!Objects.equals(r.getRequestedBy(), me) && !lookup.isDepartmentHead(r.getDepartmentId(), me)) {
            throw new AccessDeniedException("You cannot view this requisition.");
        }
    }

    private void assertRequesterOrHr(JobRequisition r, Long me) {
        if (!guard.isHrStaff() && !Objects.equals(r.getRequestedBy(), me)) {
            throw new AccessDeniedException("Only the person who raised the requisition, or HR, can do this.");
        }
    }

    private static void assertNotOwn(JobRequisition r, Long me) {
        if (Objects.equals(r.getRequestedBy(), me)) {
            throw new BusinessRuleException("SELF_APPROVAL_NOT_ALLOWED",
                "You raised requisition " + r.getCode() + ", so another person must decide on it.");
        }
    }

    /** A request that carries a version must carry the current one (HTTP 409 otherwise). */
    private static void requireCurrent(Long sent, Long current) {
        if (!Objects.equals(sent, current)) {
            throw new OptimisticLockingFailureException("Version " + sent + " is stale, current is " + current);
        }
    }

    /** Field rules that need no database, then the references (active department, designation, people). */
    private void validate(RequisitionRequest r) {
        if (r.maxExperienceYears() != null && r.maxExperienceYears().compareTo(r.minExperienceYears()) < 0) {
            throw new BadRequestException("Maximum experience cannot be below the minimum");
        }
        if (r.minSalary() != null && r.maxSalary() != null && r.maxSalary().compareTo(r.minSalary()) < 0) {
            throw new BadRequestException("Maximum salary cannot be below the minimum");
        }
        if (r.targetJoinDate() != null && r.targetJoinDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Target join date cannot be in the past");
        }
        lookup.activeDepartment(r.departmentId());
        lookup.activeDesignation(r.designationId(), r.departmentId());
        if (r.locationId() != null) lookup.requireActiveLocation(r.locationId());
        if (r.reportingManagerId() != null) lookup.requireActiveEmployee(r.reportingManagerId());
        if (r.requisitionType() == RequisitionType.REPLACEMENT) {
            if (r.replacedEmployeeId() == null) {
                throw new BusinessRuleException("REPLACED_EMPLOYEE_REQUIRED", "Name the employee being replaced.");
            }
            lookup.requireEmployeeExists(r.replacedEmployeeId());
        }
    }

    private void apply(JobRequisition r, RequisitionRequest in) {
        r.setJobTitle(in.jobTitle().trim());
        r.setJobTitleAr(Strings.trimToNull(in.jobTitleAr()));
        r.setDepartmentId(in.departmentId());
        r.setDesignationId(in.designationId());
        r.setLocationId(in.locationId());
        r.setReportingManagerId(in.reportingManagerId());
        r.setNoOfPositions(in.noOfPositions());
        r.setEmploymentType(in.employmentType());
        r.setRequisitionType(in.requisitionType());
        r.setReplacedEmployeeId(in.requisitionType() == RequisitionType.REPLACEMENT ? in.replacedEmployeeId() : null);
        r.setMinExperienceYears(in.minExperienceYears());
        r.setMaxExperienceYears(in.maxExperienceYears());
        r.setMinSalary(in.minSalary());
        r.setMaxSalary(in.maxSalary());
        r.setPriority(in.priority());
        r.setTargetJoinDate(in.targetJoinDate());
        r.setJustification(Strings.trimToNull(in.justification()));
        if (r.getFilledPositions() != null && r.getFilledPositions() > in.noOfPositions()) {
            throw new BusinessRuleException("POSITIONS_BELOW_FILLED", "Positions cannot be fewer than the ones already filled.");
        }
    }

    private String nextCode() {
        return "REQ-%d-%06d".formatted(LocalDate.now().getYear(), requisitions.nextCodeNumber());
    }

    private static Pageable pageableOf(int page, int size, String sortBy, String direction) {
        String property = SORTABLE.getOrDefault(sortBy == null ? "" : sortBy, "createdAt");
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        // The id breaks ties so paging through equal values never repeats or skips a row.
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
            Sort.by(dir, property).and(Sort.by(dir, "id")));
    }

    private static String summary(JobRequisition r) {
        return "code=" + r.getCode() + ", title=" + r.getJobTitle() + ", department=" + r.getDepartmentId()
            + ", designation=" + r.getDesignationId() + ", positions=" + r.getNoOfPositions() + ", status=" + r.getStatus();
    }

    // ------------------------------------------------------------------------------------- mapping

    private Page<RequisitionSummaryResponse> toSummaries(Page<JobRequisition> page) {
        Set<Long> departmentIds = new HashSet<>();
        Set<Long> employeeIds = new HashSet<>();
        page.forEach(r -> {
            departmentIds.add(r.getDepartmentId());
            employeeIds.add(r.getRequestedBy());
        });
        Map<Long, String> departments = lookup.departmentNames(departmentIds);
        Map<Long, String> employees = lookup.employeeNames(employeeIds);
        return page.map(r -> RequisitionSummaryResponse.builder()
            .id(r.getId())
            .code(r.getCode())
            .jobTitle(r.getJobTitle())
            .departmentId(r.getDepartmentId())
            .departmentName(departments.get(r.getDepartmentId()))
            .requestedByName(employees.get(r.getRequestedBy()))
            .noOfPositions(r.getNoOfPositions())
            .filledPositions(r.getFilledPositions())
            .priority(r.getPriority())
            .targetJoinDate(r.getTargetJoinDate())
            .status(r.getStatus())
            .createdAt(r.getCreatedAt())
            .build());
    }

    private RequisitionResponse toResponse(JobRequisition r) {
        List<RequisitionApproval> decisions = approvals.findByRequisitionIdOrderByLevelNoAsc(r.getId());
        Set<Long> employeeIds = new HashSet<>();
        employeeIds.add(r.getRequestedBy());
        if (r.getReportingManagerId() != null) employeeIds.add(r.getReportingManagerId());
        decisions.forEach(d -> employeeIds.add(d.getApproverId()));
        Map<Long, String> employees = lookup.employeeNames(employeeIds);

        List<ApprovalResponse> approvalViews = new ArrayList<>();
        for (RequisitionApproval d : decisions) {
            approvalViews.add(ApprovalResponse.builder()
                .levelNo(d.getLevelNo())
                .approverId(d.getApproverId())
                .approverName(employees.get(d.getApproverId()))
                .decision(d.getDecision())
                .budgetConfirmed(Boolean.TRUE.equals(d.getBudgetConfirmed()))
                .managementConfirmed(Boolean.TRUE.equals(d.getManagementConfirmed()))
                .comments(d.getComments())
                .actionAt(d.getActionAt())
                .build());
        }
        return RequisitionResponse.builder()
            .id(r.getId())
            .code(r.getCode())
            .jobTitle(r.getJobTitle())
            .jobTitleAr(r.getJobTitleAr())
            .departmentId(r.getDepartmentId())
            .departmentName(lookup.departmentNames(Set.of(r.getDepartmentId())).get(r.getDepartmentId()))
            .designationId(r.getDesignationId())
            .designationTitle(lookup.designationTitles(Set.of(r.getDesignationId())).get(r.getDesignationId()))
            .locationId(r.getLocationId())
            .reportingManagerId(r.getReportingManagerId())
            .reportingManagerName(r.getReportingManagerId() == null ? null : employees.get(r.getReportingManagerId()))
            .requestedById(r.getRequestedBy())
            .requestedByName(employees.get(r.getRequestedBy()))
            .noOfPositions(r.getNoOfPositions())
            .filledPositions(r.getFilledPositions())
            .employmentType(r.getEmploymentType())
            .requisitionType(r.getRequisitionType())
            .replacedEmployeeId(r.getReplacedEmployeeId())
            .minExperienceYears(r.getMinExperienceYears())
            .maxExperienceYears(r.getMaxExperienceYears())
            .minSalary(r.getMinSalary())
            .maxSalary(r.getMaxSalary())
            .currency(r.getCurrency())
            .priority(r.getPriority())
            .targetJoinDate(r.getTargetJoinDate())
            .justification(r.getJustification())
            .status(r.getStatus())
            .closedAt(r.getClosedAt())
            .createdAt(r.getCreatedAt())
            .updatedAt(r.getUpdatedAt())
            .version(r.getVersion())
            .approvals(approvalViews)
            .build();
    }
}
