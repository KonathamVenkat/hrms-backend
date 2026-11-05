package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.util.Strings;
import com.hrms.employee.service.EmployeeChecks;
import com.hrms.common.audit.CurrentAuditor;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.JobDetailsRequest;
import com.hrms.employee.dto.response.JobDetailsResponse;
import com.hrms.employee.entity.Designation;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.DesignationRepository;
import com.hrms.employee.repository.ProfessionRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.repository.OfficeLocationRepository;
import com.hrms.employee.repository.WorkShiftRepository;
import com.hrms.employee.repository.projection.JobDetailsProjection;
import com.hrms.employee.repository.CompanyBranchRepository;
import com.hrms.employee.repository.SectionRepository;
import com.hrms.employee.repository.ContractTypeRepository;
import com.hrms.employee.repository.SpecializationRepository;
import com.hrms.employee.service.DepartmentHeadService;
import com.hrms.employee.service.DepartmentSecretaryService;
import com.hrms.employee.service.JobDetailsService;
import com.hrms.employee.service.OrganisationChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobDetailsServiceImpl implements JobDetailsService {

    private final EmployeeJobDetailsRepository jobDetailsRepository;
    private final EmployeeRepository           employeeRepository;
    private final DepartmentRepository         departmentRepository;
    private final DesignationRepository        designationRepository;
    private final ProfessionRepository         professionRepository;
    private final OfficeLocationRepository     locationRepository;
    private final WorkShiftRepository          shiftRepository;
    private final DepartmentHeadService        departmentHeads;
    private final DepartmentSecretaryService   departmentSecretaries;
    private final CompanyBranchRepository      branchRepository;
    private final SectionRepository            sectionRepository;
    private final SpecializationRepository     specializationRepository;
    private final ContractTypeRepository       contractTypeRepository;
    private final ApplicationEventPublisher    events;
    private final AuditTrail                   audit;

    private static final String TARGET = "EMPLOYEE_JOB";

    // ── Get current job ───────────────────────────────────────

    @Override
    public JobDetailsResponse getCurrentJob(Long employeeId) {
        EmployeeChecks.requireExists(employeeRepository, employeeId);

        return jobDetailsRepository
            .findCurrentJobByEmployee(employeeId)
            .map(this::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException(
                "JobDetails", "employeeId", employeeId));
    }

    // ── Get full job history ──────────────────────────────────

    @Override
    public List<JobDetailsResponse> getJobHistory(Long employeeId) {
        EmployeeChecks.requireExists(employeeRepository, employeeId);

        return jobDetailsRepository
            .findJobHistoryByEmployee(employeeId)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    // ── Assign new job (SCD Type 2) ───────────────────────────

    @Override
    @Transactional
    public JobDetailsResponse assignJob(Long employeeId, JobDetailsRequest request) {
        log.info("Assigning new job to employee id: {}", employeeId);
        lockEmployee(employeeId);
        requireActiveEmployee(employeeId);
        validateAssignmentReferences(employeeId, request);
        Long professionId = resolveProfessionId(request);
        String auditor = CurrentAuditor.name();

        // ── Validate effective date ───────────────────────────
        if (request.getEffectiveFrom().isBefore(LocalDate.now().minusYears(1))) {
            throw new BusinessRuleException(
                "INVALID_DATE",
                "Effective from date cannot be more than 1 year in the past.");
        }
        // A new assignment becomes the current record immediately and nothing activates it later,
        // so a future start date would show the new department before it applies.
        if (request.getEffectiveFrom().isAfter(LocalDate.now())) {
            throw new BusinessRuleException(
                "FUTURE_EFFECTIVE_FROM",
                "Effective from date cannot be in the future. Record the assignment on or after its start date.");
        }

        // ── Close current record (SCD Type 2) ────────────────
        var currentRecord = jobDetailsRepository
            .findByEmployeeIdAndIsCurrent(employeeId, 1);
        EmployeeJobDetails prev = currentRecord.orElse(null);

        // A date left out is carried over from the record being replaced, so the end is checked against the start that will be stored.
        LocalDate contractStart = keep(request.getContractStartDate(), prev, EmployeeJobDetails::getContractStartDate);
        LocalDate contractEnd   = keep(request.getContractEndDate(), prev, EmployeeJobDetails::getContractEndDate);
        requireContractDatesInOrder(contractStart, contractEnd);

        if (currentRecord.isPresent()) {
            // The new assignment must move the SCD chain forward — an effectiveFrom on or
            // before the current record's own effectiveFrom would close that record with an
            // effectiveTo earlier than its effectiveFrom, corrupting the history's ordering.
            LocalDate currentEffectiveFrom = currentRecord.get().getEffectiveFrom();
            if (!request.getEffectiveFrom().isAfter(currentEffectiveFrom)) {
                throw new BusinessRuleException(
                    "INVALID_EFFECTIVE_FROM",
                    "Effective from date must be after the current assignment's effective from date ("
                        + currentEffectiveFrom + ").");
            }

            // Set effective_to = day before new effective_from
            LocalDate closingDate = request.getEffectiveFrom().minusDays(1);
            int closed = jobDetailsRepository.closeCurrentRecord(
                employeeId, closingDate, auditor);
            log.info("Closed {} previous job record(s) for employee {}",
                closed, employeeId);
        }

        // A new assignment in the same location keeps the employee's branch unless another one is chosen.
        Long branchId = request.getCompanyBranchId();
        if (branchId == null && currentRecord.isPresent()
                && Objects.equals(currentRecord.get().getLocationId(), request.getLocationId())) {
            branchId = currentRecord.get().getCompanyBranchId();
        }

        // ── Create new current record ─────────────────────────
        EmployeeJobDetails newJob = EmployeeJobDetails.builder()
            .employeeId(employeeId)
            .departmentId(request.getDepartmentId())
            .designationId(request.getDesignationId())
            .professionId(professionId)
            .sectionId(request.getSectionId())
            .jobPositionId(request.getJobPositionId())
            .reportingManagerId(request.getReportingManagerId())
            .functionalManagerId(request.getFunctionalManagerId())
            .locationId(request.getLocationId())
            .shiftId(request.getShiftId())
            .companyBranchId(branchId)
            .contractTypeId(keep(request.getContractTypeId(), prev, EmployeeJobDetails::getContractTypeId))
            .contractStartDate(contractStart)
            .contractEndDate(contractEnd)
            .specializationId(keep(request.getSpecializationId(), prev, EmployeeJobDetails::getSpecializationId))
            .formRequestNo(keep(Strings.trimToNull(request.getFormRequestNo()), prev, EmployeeJobDetails::getFormRequestNo))
            .appointmentRequestNo(keep(Strings.trimToNull(request.getAppointmentRequestNo()), prev, EmployeeJobDetails::getAppointmentRequestNo))
            .afe(keep(Strings.trimToNull(request.getAfe()), prev, EmployeeJobDetails::getAfe))
            .companyIdCardNo(keep(Strings.trimToNull(request.getCompanyIdCardNo()), prev, EmployeeJobDetails::getCompanyIdCardNo))
            .currentWorkAddress(keep(Strings.trimToNull(request.getCurrentWorkAddress()), prev, EmployeeJobDetails::getCurrentWorkAddress))
            .currentWorkPhone(keep(Strings.trimToNull(request.getCurrentWorkPhone()), prev, EmployeeJobDetails::getCurrentWorkPhone))
            .workMode(request.getWorkMode())
            .effectiveFrom(request.getEffectiveFrom())
            .effectiveTo(null)              // null = still active
            .isCurrent(1)
            .remarks(request.getRemarks())
            .createdBy(auditor)
            .createdAt(LocalDateTime.now())
            .build();

        EmployeeJobDetails saved = jobDetailsRepository.save(newJob);
        log.info("New job assigned. JOB_DETAILS_ID: {}", saved.getJobDetailsId());
        audit.record("EMPLOYEE_JOB_ASSIGNED", TARGET, saved.getJobDetailsId(), jobSummary(saved));
        // Moving to another department ends any headship or secretary post of the old one.
        departmentHeads.clearHeadOf(employeeId, request.getDepartmentId());
        departmentSecretaries.clearSecretaryOf(employeeId, request.getDepartmentId());
        // The branch decides the branch manager of the employee's waiting workflow requests.
        Long previousBranchId = currentRecord.map(EmployeeJobDetails::getCompanyBranchId).orElse(null);
        if (!Objects.equals(previousBranchId, branchId)) {
            events.publishEvent(new OrganisationChanged());
        }

        return jobDetailsRepository
            .findCurrentJobByEmployee(employeeId)
            .map(this::toResponse)
            .orElseThrow();
    }

    // ── Update current job (non-structural fields only) ───────

    @Override
    @Transactional
    public JobDetailsResponse updateCurrentJob(Long employeeId, JobDetailsRequest request) {
        log.info("Updating current job for employee id: {}", employeeId);

        lockEmployee(employeeId);
        requireActiveEmployee(employeeId);

        EmployeeJobDetails current = jobDetailsRepository
            .findByEmployeeIdAndIsCurrent(employeeId, 1)
            .orElseThrow(() -> new ResourceNotFoundException(
                "JobDetails", "employeeId", employeeId));

        // Structural fields are history-bearing: changing one here would rewrite the past
        // instead of starting a new SCD record, and the old code silently ignored the change
        // while reporting success. Refuse it explicitly.
        if (!Objects.equals(request.getDepartmentId(), current.getDepartmentId())
                || !Objects.equals(request.getDesignationId(), current.getDesignationId())
                // Not sent (null) means "keep"; changing a profession that is set is a new assignment, like the designation.
                || (request.getProfessionId() != null && current.getProfessionId() != null
                    && !Objects.equals(request.getProfessionId(), current.getProfessionId()))
                || !Objects.equals(request.getLocationId(), current.getLocationId())
                // A record without a section may be given one (nothing recorded is rewritten); changing a set one is a new assignment.
                || (current.getSectionId() != null && !Objects.equals(request.getSectionId(), current.getSectionId()))
                || !Objects.equals(request.getCompanyBranchId(), current.getCompanyBranchId())
                || !Objects.equals(request.getReportingManagerId(), current.getReportingManagerId())
                || !Objects.equals(request.getFunctionalManagerId(), current.getFunctionalManagerId())
                || !Objects.equals(Strings.trimToNull(request.getJobPositionId()),
                                   Strings.trimToNull(current.getJobPositionId()))) {
            throw new BusinessRuleException(
                "STRUCTURAL_CHANGE_NOT_ALLOWED",
                "Department, section, designation, position, location, branch and managers can only be changed "
                    + "with a new assignment. This update may change shift, work mode, remarks, contract, specialization and the appointment references only.");
        }
        if (request.getShiftId() != null) {
            requireActiveShift(request.getShiftId());
        }
        // A record without a profession may be given one: nothing recorded is rewritten, the value was just unknown.
        if (current.getProfessionId() == null && request.getProfessionId() != null) {
            current.setProfessionId(resolveProfessionId(request));
        }

        if (current.getSectionId() == null && request.getSectionId() != null) {
            requireSectionInDepartment(request.getSectionId(), current.getDepartmentId());
            current.setSectionId(request.getSectionId());
        }

        requireAppointmentReferences(request);

        // Update only non-structural fields (no new SCD record). The form sends all of them, so a value left empty clears it.
        current.setContractTypeId(request.getContractTypeId());
        current.setContractStartDate(request.getContractStartDate());
        current.setContractEndDate(request.getContractEndDate());
        current.setSpecializationId(request.getSpecializationId());
        current.setFormRequestNo(Strings.trimToNull(request.getFormRequestNo()));
        current.setAppointmentRequestNo(Strings.trimToNull(request.getAppointmentRequestNo()));
        current.setAfe(Strings.trimToNull(request.getAfe()));
        current.setCompanyIdCardNo(Strings.trimToNull(request.getCompanyIdCardNo()));
        current.setCurrentWorkAddress(Strings.trimToNull(request.getCurrentWorkAddress()));
        current.setCurrentWorkPhone(Strings.trimToNull(request.getCurrentWorkPhone()));
        current.setShiftId(request.getShiftId());
        current.setWorkMode(request.getWorkMode());
        current.setRemarks(request.getRemarks());
        current.setUpdatedBy(CurrentAuditor.name());
        current.setUpdatedAt(LocalDateTime.now());

        jobDetailsRepository.save(current);
        audit.record("EMPLOYEE_JOB_UPDATED", TARGET, current.getJobDetailsId(), jobSummary(current));

        return jobDetailsRepository
            .findCurrentJobByEmployee(employeeId)
            .map(this::toResponse)
            .orElseThrow();
    }

    // ── Private helpers ───────────────────────────────────────

    /**
     * Serialises concurrent job changes for one employee. Without it two assignments can both
     * read the same current row, both close it and both insert a current row, after which
     * every job lookup for that employee fails on two matches.
     */
    private void lockEmployee(Long employeeId) {
        employeeRepository.findByIdForUpdate(employeeId);
    }

    /** Job changes only make sense for an employee who is still on the books. */
    private void requireActiveEmployee(Long employeeId) {
        if (employeeRepository.findByIdAndIsActive(employeeId, true).isEmpty()) {
            EmployeeChecks.requireExists(employeeRepository, employeeId);   // 404 if it doesn't exist at all
            throw new BusinessRuleException(
                "EMP_INACTIVE",
                "This employee is deactivated. Reactivate the employee before changing job details.");
        }
    }

    /**
     * Every id in an assignment must point at something that exists and is active, the
     * designation must belong to the chosen department, and nobody can be their own manager.
     */
    private void validateAssignmentReferences(Long employeeId, JobDetailsRequest request) {
        var dept = departmentRepository.findById(request.getDepartmentId())
            .orElseThrow(() -> new ResourceNotFoundException("Department", "id", request.getDepartmentId()));
        if (Boolean.FALSE.equals(dept.getIsActive())) {
            throw new BusinessRuleException("DEPARTMENT_INACTIVE", "The selected department is inactive.");
        }

        var desig = designationRepository.findById(request.getDesignationId())
            .orElseThrow(() -> new ResourceNotFoundException("Designation", "id", request.getDesignationId()));
        if (Boolean.FALSE.equals(desig.getIsActive())) {
            throw new BusinessRuleException("DESIGNATION_INACTIVE", "The selected designation is inactive.");
        }
        if (desig.getDepartmentId() != null
                && !desig.getDepartmentId().equals(request.getDepartmentId())) {
            throw new BusinessRuleException(
                "DESIGNATION_DEPARTMENT_MISMATCH",
                "The selected designation does not belong to the selected department.");
        }

        requireSectionInDepartment(request.getSectionId(), request.getDepartmentId());
        requireAppointmentReferences(request);

        var location = locationRepository.findById(request.getLocationId())
            .orElseThrow(() -> new ResourceNotFoundException("OfficeLocation", "id", request.getLocationId()));
        if (Integer.valueOf(0).equals(location.getIsActive())) {
            throw new BusinessRuleException("LOCATION_INACTIVE", "The selected office location is inactive.");
        }

        if (request.getShiftId() != null) {
            requireActiveShift(request.getShiftId());
        }

        requireBranchInLocation(request.getCompanyBranchId(), request.getLocationId());
        requireValidManager(employeeId, request.getReportingManagerId(), "Reporting manager");
        requireValidManager(employeeId, request.getFunctionalManagerId(), "Functional manager");
    }

    /**
     * The profession of a new assignment: the one asked for, else the designation's own (which may be none).
     * A profession that is given must exist and be active, and must match the designation's when that has one.
     */
    private Long resolveProfessionId(JobDetailsRequest request) {
        Long designationProfession = designationRepository.findById(request.getDesignationId())
            .map(Designation::getProfessionId).orElse(null);
        Long requested = request.getProfessionId();
        if (requested == null) return designationProfession;
        var profession = professionRepository.findById(requested)
            .orElseThrow(() -> new ResourceNotFoundException("Profession", "id", requested));
        if (!Boolean.TRUE.equals(profession.getIsActive())) {
            throw new BusinessRuleException("PROFESSION_INACTIVE", "The selected profession is inactive.");
        }
        if (designationProfession != null && !designationProfession.equals(requested)) {
            throw new BusinessRuleException("PROFESSION_DESIGNATION_MISMATCH",
                "The selected profession does not match the profession of the selected designation.");
        }
        return requested;
    }

    /** The value asked for, else the one on the record being replaced (null when there is none). */
    private static <T> T keep(T requested, EmployeeJobDetails previous, Function<EmployeeJobDetails, T> getter) {
        return requested != null ? requested : previous == null ? null : getter.apply(previous);
    }

    /** Specialization and contract type are optional; when given they must exist and be active, and the contract end cannot precede its start. */
    private void requireAppointmentReferences(JobDetailsRequest request) {
        if (request.getSpecializationId() != null) {
            var specialization = specializationRepository.findById(request.getSpecializationId())
                .orElseThrow(() -> new ResourceNotFoundException("Specialization", "id", request.getSpecializationId()));
            if (!Boolean.TRUE.equals(specialization.getIsActive())) {
                throw new BusinessRuleException("SPECIALIZATION_INACTIVE", "The selected specialization is inactive.");
            }
        }
        if (request.getContractTypeId() != null) {
            var contractType = contractTypeRepository.findById(request.getContractTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("ContractType", "id", request.getContractTypeId()));
            if (!Boolean.TRUE.equals(contractType.getIsActive())) {
                throw new BusinessRuleException("CONTRACT_TYPE_INACTIVE", "The selected contract type is inactive.");
            }
        }
        requireContractDatesInOrder(request.getContractStartDate(), request.getContractEndDate());
    }

    private static void requireContractDatesInOrder(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessRuleException("INVALID_CONTRACT_DATES", "The contract end date cannot be before its start date.");
        }
    }

    /** Which employee and which organisation ids only: managers and references are ids, no free text or personal data. */
    private static String jobSummary(EmployeeJobDetails j) {
        return "employeeId=" + j.getEmployeeId() + ", departmentId=" + j.getDepartmentId()
            + ", designationId=" + j.getDesignationId() + ", locationId=" + j.getLocationId()
            + ", reportingManagerId=" + j.getReportingManagerId() + ", effectiveFrom=" + j.getEffectiveFrom();
    }

    /** A section is optional; when given it must exist, be active and belong to the chosen department. */
    private void requireSectionInDepartment(Long sectionId, Long departmentId) {
        if (sectionId == null) return;
        var section = sectionRepository.findById(sectionId)
            .orElseThrow(() -> new ResourceNotFoundException("Section", "id", sectionId));
        if (!Boolean.TRUE.equals(section.getIsActive())) {
            throw new BusinessRuleException("SECTION_INACTIVE", "The selected section is inactive.");
        }
        if (!section.getDepartmentId().equals(departmentId)) {
            throw new BusinessRuleException("SECTION_DEPARTMENT_MISMATCH",
                "The selected section does not belong to the selected department.");
        }
    }

    /** A branch, when given, must exist, be active and sit in the location chosen for the job. */
    private void requireBranchInLocation(Long branchId, Long locationId) {
        if (branchId == null) return;
        var branch = branchRepository.findById(branchId)
            .orElseThrow(() -> new ResourceNotFoundException("Company branch", "id", branchId));
        if (!Boolean.TRUE.equals(branch.getIsActive())) {
            throw new BusinessRuleException("BRANCH_INACTIVE", "The selected branch is inactive.");
        }
        if (!Objects.equals(branch.getLocationId(), locationId)) {
            throw new BusinessRuleException("BRANCH_LOCATION_MISMATCH",
                "The selected branch is not in the selected office location.");
        }
    }

    private void requireActiveShift(Long shiftId) {
        var shift = shiftRepository.findById(shiftId)
            .orElseThrow(() -> new ResourceNotFoundException("WorkShift", "id", shiftId));
        if (Integer.valueOf(0).equals(shift.getIsActive())) {
            throw new BusinessRuleException("SHIFT_INACTIVE", "The selected work shift is inactive.");
        }
    }

    private void requireValidManager(Long employeeId, Long managerId, String label) {
        if (managerId == null) return;
        if (managerId.equals(employeeId)) {
            throw new BusinessRuleException(
                "MANAGER_IS_SELF", label + " cannot be the employee themselves.");
        }
        if (employeeRepository.findByIdAndIsActive(managerId, true).isEmpty()) {
            throw new BusinessRuleException(
                "MANAGER_INVALID", label + " must be an existing, active employee.");
        }
    }

    private JobDetailsResponse toResponse(JobDetailsProjection p) {
        return JobDetailsResponse.builder()
            .jobDetailsId(p.getJobDetailsId())
            .employeeId(p.getEmployeeId())
            // Department
            .departmentId(p.getDepartmentId())
            .departmentName(p.getDepartmentName())
            .departmentNameAr(p.getDepartmentNameAr())
            .departmentCode(p.getDepartmentCode())
            // Designation
            .designationId(p.getDesignationId())
            .designationTitle(p.getDesignationTitle())
            .designationTitleAr(p.getDesignationTitleAr())
            .gradeLevel(p.getGradeLevel())
            // Profession
            .sectionId(p.getSectionId())
            .sectionCode(p.getSectionCode())
            .sectionName(p.getSectionName())
            .sectionNameAr(p.getSectionNameAr())
            .professionId(p.getProfessionId())
            .professionName(p.getProfessionName())
            .professionNameAr(p.getProfessionNameAr())
            // Specialization and contract
            .specializationId(p.getSpecializationId())
            .specializationName(p.getSpecializationName())
            .specializationNameAr(p.getSpecializationNameAr())
            .contractTypeId(p.getContractTypeId())
            .contractTypeName(p.getContractTypeName())
            .contractTypeNameAr(p.getContractTypeNameAr())
            .contractStartDate(p.getContractStartDate())
            .contractEndDate(p.getContractEndDate())
            // Appointment references, current work address
            .formRequestNo(p.getFormRequestNo())
            .appointmentRequestNo(p.getAppointmentRequestNo())
            .afe(p.getAfe())
            .companyIdCardNo(p.getCompanyIdCardNo())
            .currentWorkAddress(p.getCurrentWorkAddress())
            .currentWorkPhone(p.getCurrentWorkPhone())
            // Position
            .jobPositionId(p.getJobPositionId())
            // Managers
            .reportingManagerId(p.getReportingManagerId())
            .reportingManagerName(p.getReportingManagerName())
            .reportingManagerCode(p.getReportingManagerCode())
            .functionalManagerId(p.getFunctionalManagerId())
            .functionalManagerName(p.getFunctionalManagerName())
            .functionalManagerCode(p.getFunctionalManagerCode())
            // Location
            .locationId(p.getLocationId())
            .locationName(p.getLocationName())
            .locationNameAr(p.getLocationNameAr())
            .locationCode(p.getLocationCode())
            .locationCity(p.getLocationCity())
            // Shift
            .shiftId(p.getShiftId())
            .shiftName(p.getShiftName())
            .shiftStartTime(p.getShiftStartTime())
            .shiftEndTime(p.getShiftEndTime())
            // Branch
            .companyBranchId(p.getCompanyBranchId())
            .branchCode(p.getBranchCode())
            .branchName(p.getBranchName())
            .branchNameAr(p.getBranchNameAr())
            .branchManagerName(p.getBranchManagerName())
            // Work
            .workMode(p.getWorkMode())
            .effectiveFrom(p.getEffectiveFrom())
            .effectiveTo(p.getEffectiveTo())
            .isCurrent(p.getIsCurrent() == 1)
            .remarks(p.getRemarks())
            // Audit
            .createdBy(p.getCreatedBy())
            .createdAt(p.getCreatedAt())
            .build();
    }
}
