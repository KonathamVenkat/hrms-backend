package com.hrms.employee.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.JobDetailsRequest;
import com.hrms.employee.dto.response.JobDetailsResponse;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.repository.projection.JobDetailsProjection;
import com.hrms.employee.service.JobDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobDetailsServiceImpl implements JobDetailsService {

    private final EmployeeJobDetailsRepository jobDetailsRepository;
    private final EmployeeRepository           employeeRepository;

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
        validateEmployeeExists(employeeId);

        // ── Validate effective date ───────────────────────────
        if (request.getEffectiveFrom().isBefore(LocalDate.now().minusYears(1))) {
            throw new BusinessRuleException(
                "INVALID_DATE",
                "Effective from date cannot be more than 1 year in the past.");
        }

        // ── Close current record (SCD Type 2) ────────────────
        boolean hasCurrent = jobDetailsRepository
            .findByEmployeeIdAndIsCurrent(employeeId, 1)
            .isPresent();

        if (hasCurrent) {
            // Set effective_to = day before new effective_from
            LocalDate closingDate = request.getEffectiveFrom().minusDays(1);
            int closed = jobDetailsRepository.closeCurrentRecord(
                employeeId, closingDate, "SYSTEM");
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
            .createdBy("SYSTEM")
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

        EmployeeJobDetails current = jobDetailsRepository
            .findByEmployeeIdAndIsCurrent(employeeId, 1)
            .orElseThrow(() -> new ResourceNotFoundException(
                "JobDetails", "employeeId", employeeId));

        // Update only non-structural fields (no new SCD record)
        current.setShiftId(request.getShiftId());
        current.setWorkMode(request.getWorkMode());
        current.setRemarks(request.getRemarks());
        current.setUpdatedBy("SYSTEM");
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
