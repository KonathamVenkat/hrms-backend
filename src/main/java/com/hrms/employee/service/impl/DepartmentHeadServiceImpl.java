package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.response.DepartmentHeadResponse;
import com.hrms.employee.entity.Department;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.DepartmentHeadService;
import com.hrms.employee.service.EmployeeChecks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentHeadServiceImpl implements DepartmentHeadService {

    private static final String TARGET = "DEPARTMENT";

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeJobDetailsRepository jobDetailsRepository;
    private final AuditTrail audit;

    @Override
    public DepartmentHeadResponse get(Long employeeId) {
        EmployeeChecks.requireExists(employeeRepository, employeeId);
        return toResponse(employeeId, currentDepartment(employeeId));
    }

    @Override
    @Transactional
    public DepartmentHeadResponse set(Long employeeId, boolean head) {
        var employee = employeeRepository.findByIdForUpdate(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", employeeId));
        if (!Boolean.TRUE.equals(employee.getIsActive())) {
            throw new BusinessRuleException("EMP_INACTIVE",
                "This employee is deactivated. Reactivate the employee before changing the department head.");
        }
        Department dept = currentDepartment(employeeId);
        boolean isHead = employeeId.equals(dept.getHeadEmployeeId());
        if (head && !isHead) {
            if (!Boolean.TRUE.equals(dept.getIsActive())) {
                throw new BusinessRuleException("DEPARTMENT_INACTIVE", "The department is inactive.");
            }
            change(dept, employeeId, "DEPARTMENT_HEAD_SET", dept.getHeadEmployeeId());
        } else if (!head && isHead) {
            change(dept, null, "DEPARTMENT_HEAD_CLEARED", employeeId);
        }
        return toResponse(employeeId, dept);
    }

    @Override
    @Transactional
    public void clearHeadOf(Long employeeId, Long keepDepartmentId) {
        departmentRepository.findByHeadEmployeeId(employeeId).stream()
            .filter(d -> !d.getDeptId().equals(keepDepartmentId))
            .forEach(d -> change(d, null, "DEPARTMENT_HEAD_CLEARED", employeeId));
    }

    private void change(Department dept, Long newHeadId, String action, Long previousHeadId) {
        dept.setHeadEmployeeId(newHeadId);
        dept.setUpdatedAt(LocalDateTime.now());
        departmentRepository.saveAndFlush(dept);
        audit.record(action, TARGET, dept.getDeptId(), "head=" + previousHeadId + " -> " + newHeadId);
    }

    private Department currentDepartment(Long employeeId) {
        Long deptId = jobDetailsRepository.findByEmployeeIdAndIsCurrent(employeeId, 1)
            .orElseThrow(() -> new ResourceNotFoundException("JobDetails", "employeeId", employeeId))
            .getDepartmentId();
        return departmentRepository.findById(deptId)
            .orElseThrow(() -> new ResourceNotFoundException("Department", "id", deptId));
    }

    private DepartmentHeadResponse toResponse(Long employeeId, Department dept) {
        Long headId = dept.getHeadEmployeeId();
        String headName = headId == null ? null
            : employeeRepository.findById(headId).map(e -> e.getFullName()).orElse(null);
        return new DepartmentHeadResponse(dept.getDeptId(), dept.getName(), dept.getCode(),
            employeeId.equals(headId), headId, headName);
    }
}
