package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.audit.CurrentAuditor;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.BranchAssignmentRequest;
import com.hrms.employee.dto.request.BranchPlacementRequest;
import com.hrms.employee.dto.response.BranchAssignmentResult;
import com.hrms.employee.dto.response.BranchAssignmentResult.Skipped;
import com.hrms.employee.dto.response.BranchLookupResponse;
import com.hrms.employee.dto.response.BranchPlacementResponse;
import com.hrms.employee.dto.response.EmployeeBranchGapResponse;
import com.hrms.employee.dto.response.OrganisationSummaryResponse;
import com.hrms.employee.entity.CompanyBranch;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.entity.OfficeLocation;
import com.hrms.employee.repository.CompanyBranchRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.repository.OfficeLocationRepository;
import com.hrms.employee.repository.projection.EmployeeBranchGapProjection;
import com.hrms.employee.service.OrganisationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganisationServiceImpl implements OrganisationService {

    private static final String TARGET = "COMPANY_BRANCH";
    private static final int MAX_PAGE_SIZE = 100;

    private final CompanyBranchRepository branchRepository;
    private final OfficeLocationRepository locationRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeJobDetailsRepository jobDetailsRepository;
    private final AuditTrail audit;

    @Override
    public List<BranchPlacementResponse> listBranches() {
        List<CompanyBranch> branches = branchRepository.findAllByOrderByNameAsc();
        Map<Long, OfficeLocation> locations = byId(locationRepository.findAllById(
            ids(branches, CompanyBranch::getLocationId)), OfficeLocation::getLocationId);
        Map<Long, Employee> managers = byId(employeeRepository.findAllById(
            ids(branches, CompanyBranch::getBranchManagerEmployeeId)), Employee::getId);
        return branches.stream()
            .map(b -> toResponse(b, locations.get(b.getLocationId()), managers.get(b.getBranchManagerEmployeeId())))
            .toList();
    }

    @Override
    public List<BranchLookupResponse> lookupBranches(Long locationId) {
        List<CompanyBranch> branches = locationId == null
            ? branchRepository.findByIsActiveTrueOrderByNameAsc()
            : branchRepository.findByLocationIdAndIsActiveTrueOrderByNameAsc(locationId);
        return branches.stream()
            .map(b -> new BranchLookupResponse(b.getId(), b.getName(), b.getNameAr(), b.getLocationId()))
            .toList();
    }

    @Override
    @Transactional
    public BranchPlacementResponse setPlacement(Long branchId, BranchPlacementRequest request) {
        CompanyBranch branch = branchRepository.findById(branchId)
            .orElseThrow(() -> new ResourceNotFoundException("Company branch", "id", branchId));
        Versions.requireCurrent(request.version(), branch.getVersion());
        if (!Boolean.TRUE.equals(branch.getIsActive())) {
            throw new BusinessRuleException("BRANCH_INACTIVE",
                "Branch " + branch.getCode() + " is inactive. Activate it before changing its location or manager.");
        }
        OfficeLocation location = locationRepository.findById(request.locationId())
            .orElseThrow(() -> new ResourceNotFoundException("Office location", "id", request.locationId()));
        if (branch.getLocationId() != null && !branch.getLocationId().equals(request.locationId())
                && jobDetailsRepository.countCurrentJobsInBranch(branchId) > 0) {
            // Employees keep the location of their job; a branch that moves away would leave them in the wrong one.
            throw new BusinessRuleException("BRANCH_HAS_EMPLOYEES",
                "Branch " + branch.getCode() + " still has active employees. Move them to another branch before "
                    + "changing its office location.");
        }
        Employee manager = request.managerEmployeeId() == null ? null : activeManager(request.managerEmployeeId());

        branch.setLocationId(request.locationId());
        branch.setBranchManagerEmployeeId(request.managerEmployeeId());
        branch.setUpdatedAt(LocalDateTime.now());
        branchRepository.save(branch);
        branchRepository.flush(); // so the response carries the incremented version
        audit.record("BRANCH_PLACEMENT_SET", TARGET, branchId,
            "location=" + request.locationId() + ", manager=" + request.managerEmployeeId());
        return toResponse(branch, location, manager);
    }

    @Override
    public OrganisationSummaryResponse summary() {
        return new OrganisationSummaryResponse(
            jobDetailsRepository.countEmployeesWithoutBranch(),
            branchRepository.countByIsActiveTrueAndBranchManagerEmployeeIdIsNull(),
            branchRepository.countByIsActiveTrueAndLocationIdIsNull());
    }

    @Override
    public Page<EmployeeBranchGapResponse> employeesWithoutBranch(String q, Pageable pageable) {
        // The query fixes its own ORDER BY (employee code); only the page number and a capped size are used.
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
        String keyword = q == null || q.isBlank() ? null : EmployeeServiceImpl.escapeLike(q.trim());
        return jobDetailsRepository.findEmployeesWithoutBranch(keyword, capped).map(OrganisationServiceImpl::toGap);
    }

    @Override
    public Page<EmployeeBranchGapResponse> managerCandidates(Long locationId, String q, Pageable pageable) {
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
        String keyword = q == null || q.isBlank() ? null : EmployeeServiceImpl.escapeLike(q.trim());
        return jobDetailsRepository.findManagerCandidates(locationId, keyword, capped)
            .map(OrganisationServiceImpl::toGap);
    }

    @Override
    @Transactional
    public BranchAssignmentResult assignBranches(BranchAssignmentRequest request) {
        String auditor = CurrentAuditor.name();
        int assigned = 0;
        List<Skipped> skipped = new ArrayList<>();
        for (BranchAssignmentRequest.Item item : request.items()) {
            String reason = assignOne(item.employeeId(), item.branchId(), auditor);
            if (reason == null) {
                assigned++;
            } else {
                skipped.add(new Skipped(item.employeeId(), reason));
            }
        }
        return new BranchAssignmentResult(assigned, skipped);
    }

    /** Applies one row; returns null when applied, otherwise the reason it was skipped. */
    private String assignOne(Long employeeId, Long branchId, String auditor) {
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) return "EMPLOYEE_NOT_FOUND";
        if (!Boolean.TRUE.equals(employee.getIsActive())) return "EMPLOYEE_INACTIVE";
        EmployeeJobDetails job = jobDetailsRepository.findByEmployeeIdAndIsCurrent(employeeId, 1).orElse(null);
        if (job == null) return "NO_CURRENT_JOB";
        if (job.getCompanyBranchId() != null) return "ALREADY_ASSIGNED";
        CompanyBranch branch = branchRepository.findById(branchId).orElse(null);
        if (branch == null) return "BRANCH_NOT_FOUND";
        if (!Boolean.TRUE.equals(branch.getIsActive())) return "BRANCH_INACTIVE";
        if (!Objects.equals(branch.getLocationId(), job.getLocationId())) return "BRANCH_LOCATION_MISMATCH";
        if (jobDetailsRepository.setBranchOnCurrentJob(employeeId, branchId, auditor) == 0) return "ALREADY_ASSIGNED";
        audit.record("EMPLOYEE_BRANCH_ASSIGNED", "EMPLOYEE", employeeId, "branch=" + branchId);
        return null;
    }

    @Override
    @Transactional
    public void clearManagerOf(Long employeeId) {
        for (CompanyBranch branch : branchRepository.findByBranchManagerEmployeeId(employeeId)) {
            branch.setBranchManagerEmployeeId(null);
            branch.setUpdatedAt(LocalDateTime.now());
            branchRepository.save(branch);
            audit.record("BRANCH_MANAGER_CLEARED", TARGET, branch.getId(), "manager=" + employeeId);
        }
    }

    private Employee activeManager(Long employeeId) {
        Employee e = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", employeeId));
        if (!Boolean.TRUE.equals(e.getIsActive())) {
            throw new BusinessRuleException("MANAGER_NOT_ACTIVE",
                "This employee is deactivated and cannot manage a branch.");
        }
        return e;
    }

    private static Set<Long> ids(List<CompanyBranch> branches, Function<CompanyBranch, Long> field) {
        return branches.stream().map(field).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static <T> Map<Long, T> byId(List<T> rows, Function<T, Long> id) {
        return rows.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private static EmployeeBranchGapResponse toGap(EmployeeBranchGapProjection p) {
        return new EmployeeBranchGapResponse(p.getEmployeeId(), p.getEmployeeCode(),
            p.getFirstName() + " " + p.getLastName(), p.getFirstNameAr() + " " + p.getLastNameAr(),
            p.getDepartmentName(), p.getLocationId(), p.getLocationName());
    }

    private static BranchPlacementResponse toResponse(CompanyBranch b, OfficeLocation location, Employee manager) {
        return new BranchPlacementResponse(
            b.getId(), b.getCode(), b.getName(), b.getNameAr(), b.getCompanyId(),
            b.getLocationId(),
            location == null ? null : location.getLocationName(),
            location == null ? null : location.getLocationNameAr(),
            b.getBranchManagerEmployeeId(),
            manager == null ? null : manager.getFullName(),
            manager == null ? null : manager.getEmployeeCode(),
            b.getIsActive(), b.getVersion());
    }
}
