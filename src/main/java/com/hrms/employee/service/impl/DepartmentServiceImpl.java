package com.hrms.employee.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.DepartmentRequest;
import com.hrms.employee.dto.response.DepartmentResponse;
import com.hrms.employee.entity.Department;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.DesignationRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final EmployeeJobDetailsRepository jobDetailsRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public List<DepartmentResponse> getAllDepartments() {
        return departmentRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Override
    public DepartmentResponse getDepartmentById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (departmentRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Department", "code", code);
        }
        checkHeadEmployee(request.getHeadEmployeeId());

        Department entity = Department.builder().build();
        apply(entity, request, code);
        entity.setIsActive(request.getIsActive() == null || request.getIsActive() ? 1 : 0);

        Department saved = departmentRepository.save(entity);
        log.info("Department created. ID: {}, code: {}", saved.getDeptId(), saved.getCode());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public DepartmentResponse updateDepartment(Long id, DepartmentRequest request) {
        Department existing = findById(id);
        String code = request.getCode().trim().toUpperCase();
        if (departmentRepository.existsByCodeIgnoreCaseAndDeptIdNot(code, id)) {
            throw new DuplicateResourceException("Department", "code", code);
        }
        checkHeadEmployee(request.getHeadEmployeeId());

        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive() ? 1 : 0);
        existing.setUpdatedAt(LocalDateTime.now());
        return toResponse(departmentRepository.save(existing));
    }

    @Override
    @Transactional
    public void deactivateDepartment(Long id) {
        setActive(id, 0);
    }

    @Override
    @Transactional
    public void activateDepartment(Long id) {
        setActive(id, 1);
    }

    @Override
    @Transactional
    public void deleteDepartment(Long id) {
        Department dept = findById(id);
        if (designationRepository.existsByDepartmentId(id) || jobDetailsRepository.existsByDepartmentId(id)) {
            throw new BusinessRuleException("DEPARTMENT_IN_USE",
                "Department " + dept.getCode() + " is used by designations or employee job records. "
                    + "Deactivate it instead of deleting it.");
        }
        departmentRepository.delete(dept);
        log.info("Department deleted. ID: {}, code: {}", id, dept.getCode());
    }

    private void setActive(Long id, int value) {
        Department dept = findById(id);
        dept.setIsActive(value);
        dept.setUpdatedAt(LocalDateTime.now());
        departmentRepository.save(dept);
    }

    private void checkHeadEmployee(Long headEmployeeId) {
        if (headEmployeeId != null && !employeeRepository.existsById(headEmployeeId)) {
            throw new ResourceNotFoundException("Employee", "id", headEmployeeId);
        }
    }

    private Department findById(Long id) {
        return departmentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Department", "id", id));
    }

    private void apply(Department d, DepartmentRequest r, String code) {
        d.setCode(code);
        d.setName(r.getName().trim());
        d.setNameAr(r.getNameAr().trim());
        d.setDescription(r.getDescription());
        d.setHeadEmployeeId(r.getHeadEmployeeId());
        d.setCostCenterCode(r.getCostCenterCode());
    }

    private DepartmentResponse toResponse(Department d) {
        return DepartmentResponse.builder()
            .deptId(d.getDeptId())
            .code(d.getCode())
            .name(d.getName())
            .nameAr(d.getNameAr())
            .description(d.getDescription())
            .headEmployeeId(d.getHeadEmployeeId())
            .costCenterCode(d.getCostCenterCode())
            .isActive(d.getIsActive() != null && d.getIsActive() == 1)
            .createdAt(d.getCreatedAt() != null ? d.getCreatedAt().toString() : null)
            .updatedAt(d.getUpdatedAt() != null ? d.getUpdatedAt().toString() : null)
            .build();
    }
}
