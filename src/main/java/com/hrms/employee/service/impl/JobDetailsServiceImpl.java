package com.hrms.employee.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.JobDetailsRequest;
import com.hrms.employee.dto.response.JobDetailsResponse;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.DesignationRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.repository.OfficeLocationRepository;
import com.hrms.employee.repository.WorkShiftRepository;
import com.hrms.employee.repository.projection.JobDetailsProjection;
import com.hrms.employee.service.JobDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
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
    private final OfficeLocationRepository     locationRepository;
    private final WorkShiftRepository          shiftRepository;

    // ── Get current job ───────────────────────────────────────

    @Override
    public JobDetailsResponse getCurrentJob(Long employeeId) {
        validateEmployeeExists(employeeId);

        return jobDetailsRepository
            .findCurrentJobByEmployee(employeeId)
            .map(this::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException(
                "JobDetails", "employeeId", employeeId));
    }

    // ── Get full job history ──────────────────────────────────

    @Override
    public List<JobDetailsResponse> getJobHistory(Long employeeId) {
        validateEmployeeExists(employeeId);

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
        requireActiveEmployee(employeeId);
        validateAssignmentReferences(employeeId, request);
        String auditor = getCurrentAuditor();

        // ── Validate effective date ───────────────────────────
        if (request.getEffectiveFrom().isBefore(LocalDate.now().minusYears(1))) {
            throw new BusinessRuleException(
                "INVALID_DATE",
                "Effective from date cannot be more than 1 year in the past.");
        }

        // ── Close current record (SCD Type 2) ────────────────
        var currentRecord = jobDetailsRepository
            .findByEmployeeIdAndIsCurrent(employeeId, 1);

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

        // ── Create new current record ─────────────────────────
        EmployeeJobDetails newJob = EmployeeJobDetails.builder()
            .employeeId(employeeId)
            .departmentId(request.getDepartmentId())
            .designationId(request.getDesignationId())
            .jobPositionId(request.getJobPositionId())
            .reportingManagerId(request.getReportingManagerId())
            .functionalManagerId(request.getFunctionalManagerId())
            .locationId(request.getLocationId())
            .shiftId(request.getShiftId())
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

        // TODO: Kafka → EmployeeJobChangedEvent (notification to manager)

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
                || !Objects.equals(request.getLocationId(), current.getLocationId())
                || !Objects.equals(request.getReportingManagerId(), current.getReportingManagerId())
                || !Objects.equals(request.getFunctionalManagerId(), current.getFunctionalManagerId())
                || !Objects.equals(blankToNull(request.getJobPositionId()),
                                   blankToNull(current.getJobPositionId()))) {
            throw new BusinessRuleException(
                "STRUCTURAL_CHANGE_NOT_ALLOWED",
                "Department, designation, position, location and managers can only be changed "
                    + "with a new assignment. This update may change shift, work mode and remarks only.");
        }
        if (request.getShiftId() != null) {
            requireActiveShift(request.getShiftId());
        }

        // Update only non-structural fields (no new SCD record)
        current.setShiftId(request.getShiftId());
        current.setWorkMode(request.getWorkMode());
        current.setRemarks(request.getRemarks());
        current.setUpdatedBy(getCurrentAuditor());
        current.setUpdatedAt(LocalDateTime.now());

        jobDetailsRepository.save(current);

        return jobDetailsRepository
            .findCurrentJobByEmployee(employeeId)
            .map(this::toResponse)
            .orElseThrow();
    }

    // ── Private helpers ───────────────────────────────────────

    private void validateEmployeeExists(Long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee", "id", employeeId);
        }
    }

    /** Job changes only make sense for an employee who is still on the books. */
    private void requireActiveEmployee(Long employeeId) {
        if (employeeRepository.findByIdAndIsActive(employeeId, true).isEmpty()) {
            validateEmployeeExists(employeeId);   // 404 if it doesn't exist at all
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
        if (Integer.valueOf(0).equals(dept.getIsActive())) {
            throw new BusinessRuleException("DEPARTMENT_INACTIVE", "The selected department is inactive.");
        }

        var desig = designationRepository.findById(request.getDesignationId())
            .orElseThrow(() -> new ResourceNotFoundException("Designation", "id", request.getDesignationId()));
        if (Integer.valueOf(0).equals(desig.getIsActive())) {
            throw new BusinessRuleException("DESIGNATION_INACTIVE", "The selected designation is inactive.");
        }
        if (desig.getDepartmentId() != null
                && !desig.getDepartmentId().equals(request.getDepartmentId())) {
            throw new BusinessRuleException(
                "DESIGNATION_DEPARTMENT_MISMATCH",
                "The selected designation does not belong to the selected department.");
        }

        var location = locationRepository.findById(request.getLocationId())
            .orElseThrow(() -> new ResourceNotFoundException("OfficeLocation", "id", request.getLocationId()));
        if (Integer.valueOf(0).equals(location.getIsActive())) {
            throw new BusinessRuleException("LOCATION_INACTIVE", "The selected office location is inactive.");
        }

        if (request.getShiftId() != null) {
            requireActiveShift(request.getShiftId());
        }

        requireValidManager(employeeId, request.getReportingManagerId(), "Reporting manager");
        requireValidManager(employeeId, request.getFunctionalManagerId(), "Functional manager");
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

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return "SYSTEM";
        return auth.getName();
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
