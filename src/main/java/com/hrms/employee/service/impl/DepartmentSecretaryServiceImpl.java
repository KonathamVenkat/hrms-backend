package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.response.DepartmentSecretaryResponse;
import com.hrms.employee.entity.Department;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.DepartmentSecretaryService;
import com.hrms.employee.service.EmployeeChecks;
import com.hrms.employee.service.OrganisationChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentSecretaryServiceImpl implements DepartmentSecretaryService {

    private static final String TARGET = "DEPARTMENT";

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeJobDetailsRepository jobDetailsRepository;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;

    @Override
    public DepartmentSecretaryResponse get(Long employeeId) {
        EmployeeChecks.requireExists(employeeRepository, employeeId);
        return toResponse(employeeId, currentDepartment(employeeId));
    }

    @Override
    @Transactional
    public DepartmentSecretaryResponse set(Long employeeId, boolean secretary) {
        var employee = employeeRepository.findByIdForUpdate(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", employeeId));
        if (!Boolean.TRUE.equals(employee.getIsActive())) {
            throw new BusinessRuleException("EMP_INACTIVE",
                "This employee is deactivated. Reactivate the employee before changing the department secretary.");
        }
        Department dept = currentDepartment(employeeId);
        boolean isSecretary = employeeId.equals(dept.getSecretaryEmployeeId());
        if (secretary && !isSecretary) {
            if (!Boolean.TRUE.equals(dept.getIsActive())) {
                throw new BusinessRuleException("DEPARTMENT_INACTIVE", "The department is inactive.");
            }
            change(dept, employeeId, "DEPARTMENT_SECRETARY_SET", dept.getSecretaryEmployeeId());
        } else if (!secretary && isSecretary) {
            change(dept, null, "DEPARTMENT_SECRETARY_CLEARED", employeeId);
        }
        return toResponse(employeeId, dept);
    }

    @Override
    @Transactional
    public void clearSecretaryOf(Long employeeId, Long keepDepartmentId) {
        departmentRepository.findBySecretaryEmployeeId(employeeId).stream()
            .filter(d -> !d.getDeptId().equals(keepDepartmentId))
            .forEach(d -> change(d, null, "DEPARTMENT_SECRETARY_CLEARED", employeeId));
    }

    private void change(Department dept, Long newSecretaryId, String action, Long previousSecretaryId) {
        dept.setSecretaryEmployeeId(newSecretaryId);
        dept.setUpdatedAt(LocalDateTime.now());
        departmentRepository.saveAndFlush(dept);
        events.publishEvent(new OrganisationChanged());
        audit.record(action, TARGET, dept.getDeptId(), "secretary=" + previousSecretaryId + " -> " + newSecretaryId);
    }

    private Department currentDepartment(Long employeeId) {
        Long deptId = jobDetailsRepository.findByEmployeeIdAndIsCurrent(employeeId, 1)
            .orElseThrow(() -> new ResourceNotFoundException("JobDetails", "employeeId", employeeId))
            .getDepartmentId();
        return departmentRepository.findById(deptId)
            .orElseThrow(() -> new ResourceNotFoundException("Department", "id", deptId));
    }

    private DepartmentSecretaryResponse toResponse(Long employeeId, Department dept) {
        Long secretaryId = dept.getSecretaryEmployeeId();
        String name = secretaryId == null ? null
            : employeeRepository.findById(secretaryId).map(e -> e.getFullName()).orElse(null);
        return new DepartmentSecretaryResponse(dept.getDeptId(), dept.getName(), dept.getCode(),
            employeeId.equals(secretaryId), secretaryId, name);
    }
}
