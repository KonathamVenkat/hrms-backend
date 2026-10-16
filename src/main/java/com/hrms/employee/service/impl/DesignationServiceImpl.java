package com.hrms.employee.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.DesignationRequest;
import com.hrms.employee.dto.response.DesignationResponse;
import com.hrms.employee.entity.Department;
import com.hrms.employee.entity.Designation;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.DesignationRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.service.DesignationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DesignationServiceImpl implements DesignationService {

    private final DesignationRepository designationRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeJobDetailsRepository jobDetailsRepository;

    @Override
    public List<DesignationResponse> getAllDesignations() {
        Map<Long, String> departmentNames = departmentRepository.findAll().stream()
            .collect(Collectors.toMap(Department::getDeptId, Department::getName, (a, b) -> a));
        return designationRepository.findAllByOrderByTitleAsc().stream()
            .map(d -> toResponse(d, departmentNames.get(d.getDepartmentId())))
            .toList();
    }

    @Override
    public DesignationResponse getDesignationById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public DesignationResponse createDesignation(DesignationRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (designationRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Designation", "code", code);
        }
        checkDepartment(request.getDepartmentId());

        Designation entity = Designation.builder().build();
        apply(entity, request, code);
        entity.setIsActive(request.getIsActive() == null || request.getIsActive() ? 1 : 0);

        Designation saved = designationRepository.save(entity);
        log.info("Designation created. ID: {}, code: {}", saved.getDesigId(), saved.getCode());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public DesignationResponse updateDesignation(Long id, DesignationRequest request) {
        Designation existing = findById(id);
        String code = request.getCode().trim().toUpperCase();
        if (designationRepository.existsByCodeIgnoreCaseAndDesigIdNot(code, id)) {
            throw new DuplicateResourceException("Designation", "code", code);
        }
        checkDepartment(request.getDepartmentId());

        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive() ? 1 : 0);
        existing.setUpdatedAt(LocalDateTime.now());
        return toResponse(designationRepository.save(existing));
    }

    @Override
    @Transactional
    public void deactivateDesignation(Long id) {
        setActive(id, 0);
    }

    @Override
    @Transactional
    public void activateDesignation(Long id) {
        setActive(id, 1);
    }

    @Override
    @Transactional
    public void deleteDesignation(Long id) {
        Designation desig = findById(id);
        if (jobDetailsRepository.existsByDesignationId(id)) {
            throw new BusinessRuleException("DESIGNATION_IN_USE",
                "Designation " + desig.getCode() + " is used by employee job records. "
                    + "Deactivate it instead of deleting it.");
        }
        designationRepository.delete(desig);
        log.info("Designation deleted. ID: {}, code: {}", id, desig.getCode());
    }

    private void setActive(Long id, int value) {
        Designation desig = findById(id);
        desig.setIsActive(value);
        desig.setUpdatedAt(LocalDateTime.now());
        designationRepository.save(desig);
    }

    private void checkDepartment(Long departmentId) {
        if (departmentId != null && !departmentRepository.existsById(departmentId)) {
            throw new ResourceNotFoundException("Department", "id", departmentId);
        }
    }

    private Designation findById(Long id) {
        return designationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Designation", "id", id));
    }

    private void apply(Designation d, DesignationRequest r, String code) {
        d.setCode(code);
        d.setTitle(r.getTitle().trim());
        d.setTitleAr(r.getTitleAr() == null || r.getTitleAr().isBlank() ? null : r.getTitleAr().trim());
        d.setGradeLevel(r.getGradeLevel() == null || r.getGradeLevel().isBlank() ? null : r.getGradeLevel().trim());
        d.setDepartmentId(r.getDepartmentId());
    }

    private DesignationResponse toResponse(Designation d) {
        String departmentName = d.getDepartmentId() == null ? null
            : departmentRepository.findById(d.getDepartmentId()).map(Department::getName).orElse(null);
        return toResponse(d, departmentName);
    }

    private DesignationResponse toResponse(Designation d, String departmentName) {
        return DesignationResponse.builder()
            .desigId(d.getDesigId())
            .code(d.getCode())
            .title(d.getTitle())
            .titleAr(d.getTitleAr())
            .gradeLevel(d.getGradeLevel())
            .departmentId(d.getDepartmentId())
            .departmentName(departmentName)
            .isActive(d.getIsActive() != null && d.getIsActive() == 1)
            .createdAt(d.getCreatedAt() != null ? d.getCreatedAt().toString() : null)
            .updatedAt(d.getUpdatedAt() != null ? d.getUpdatedAt().toString() : null)
            .build();
    }
}
