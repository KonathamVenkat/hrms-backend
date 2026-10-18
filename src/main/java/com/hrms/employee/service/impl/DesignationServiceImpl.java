package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.util.Strings;
import com.hrms.employee.dto.request.DesignationRequest;
import com.hrms.employee.dto.response.DesignationLookupResponse;
import com.hrms.employee.dto.response.DesignationResponse;
import com.hrms.employee.entity.Department;
import com.hrms.employee.entity.Designation;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.DesignationRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.service.DesignationService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DesignationServiceImpl implements DesignationService {

    private static final String TARGET = "DESIGNATION";

    private final DesignationRepository designationRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeJobDetailsRepository jobDetailsRepository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<DesignationResponse> getAllDesignations() {
        Map<Long, String> departmentNames = departmentRepository.findAll().stream()
            .collect(Collectors.toMap(Department::getDeptId, Department::getName, (a, b) -> a));
        return designationRepository.findAllByOrderByTitleAsc().stream()
            .map(d -> toResponse(d, departmentNames.get(d.getDepartmentId())))
            .toList();
    }

    @Override
    public List<DesignationLookupResponse> getActiveLookup() {
        return designationRepository.findByIsActiveOrderByTitleAsc(true).stream().map(this::toLookup).toList();
    }

    @Override
    public List<DesignationLookupResponse> getActiveLookupByDepartment(Long departmentId) {
        return designationRepository.findByDepartmentIdAndIsActiveOrderByTitleAsc(departmentId, true).stream()
            .map(this::toLookup).toList();
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
        boolean active = request.getIsActive() == null || request.getIsActive();
        checkDepartment(request.getDepartmentId(), active);

        Designation entity = Designation.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        Designation saved = designationRepository.save(entity);
        // The insert trigger stamps CREATED_AT / UPDATED_AT; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Designation created. ID: {}, code: {}", saved.getDesigId(), saved.getCode());
        audit.record("DESIGNATION_CREATED", TARGET, saved.getDesigId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public DesignationResponse updateDesignation(Long id, DesignationRequest request) {
        Designation existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a designation");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (designationRepository.existsByCodeIgnoreCaseAndDesigIdNot(code, id)) {
            throw new DuplicateResourceException("Designation", "code", code);
        }
        boolean active = request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE.equals(existing.getIsActive());
        checkDepartment(request.getDepartmentId(), active);
        // Moving an in-use designation would leave employees holding a department/designation pair that no longer matches.
        if (!Objects.equals(request.getDepartmentId(), existing.getDepartmentId())
                && jobDetailsRepository.existsByDesignationId(id)) {
            throw new BusinessRuleException("DESIGNATION_IN_USE",
                "Designation " + existing.getCode() + " is used by employee job records, so its department cannot change.");
        }

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        Designation saved = designationRepository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("DESIGNATION_UPDATED", TARGET, id, before + " -> " + summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deactivateDesignation(Long id) {
        setActive(id, false);
    }

    @Override
    @Transactional
    public void activateDesignation(Long id) {
        setActive(id, true);
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
        audit.record("DESIGNATION_DELETED", TARGET, id, summary(desig));
    }

    private void setActive(Long id, boolean active) {
        Designation desig = findById(id);
        if (active) checkDepartment(desig.getDepartmentId(), true);
        desig.setIsActive(active);
        desig.setUpdatedAt(LocalDateTime.now());
        designationRepository.save(desig);
        audit.record(active ? "DESIGNATION_ACTIVATED" : "DESIGNATION_DEACTIVATED", TARGET, id, desig.getCode());
    }

    private static String summary(Designation d) {
        return "code=" + d.getCode() + ", title=" + d.getTitle() + ", titleAr=" + d.getTitleAr()
            + ", grade=" + d.getGradeLevel() + ", department=" + d.getDepartmentId()
            + ", active=" + d.getIsActive();
    }

    /** The department must exist, and an active designation needs an active department. */
    private void checkDepartment(Long departmentId, boolean designationActive) {
        if (departmentId == null) return;
        Department dept = departmentRepository.findById(departmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Department", "id", departmentId));
        if (designationActive && !Boolean.TRUE.equals(dept.getIsActive())) {
            throw new BusinessRuleException("DEPARTMENT_INACTIVE",
                "Department " + dept.getCode() + " is inactive, so it cannot hold an active designation.");
        }
    }

    private Designation findById(Long id) {
        return designationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Designation", "id", id));
    }

    private void apply(Designation d, DesignationRequest r, String code) {
        d.setCode(code);
        d.setTitle(r.getTitle().trim());
        d.setTitleAr(Strings.trimToNull(r.getTitleAr()));
        d.setGradeLevel(Strings.trimToNull(r.getGradeLevel()));
        d.setDepartmentId(r.getDepartmentId());
    }

    private DesignationLookupResponse toLookup(Designation d) {
        return DesignationLookupResponse.builder()
            .id(d.getDesigId())
            .code(d.getCode())
            .title(d.getTitle())
            .titleAr(d.getTitleAr())
            .gradeLevel(d.getGradeLevel())
            .departmentId(d.getDepartmentId())
            .build();
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
            .isActive(Boolean.TRUE.equals(d.getIsActive()))
            .createdAt(d.getCreatedAt() != null ? d.getCreatedAt().toString() : null)
            .updatedAt(d.getUpdatedAt() != null ? d.getUpdatedAt().toString() : null)
            .version(d.getVersion())
            .build();
    }
}
