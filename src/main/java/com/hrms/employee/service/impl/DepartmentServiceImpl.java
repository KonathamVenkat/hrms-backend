package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.util.Strings;
import com.hrms.employee.dto.request.DepartmentRequest;
import com.hrms.employee.dto.response.DepartmentLookupResponse;
import com.hrms.employee.dto.response.DepartmentResponse;
import com.hrms.employee.dto.response.ManagerLookupResponse;
import com.hrms.employee.entity.Department;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.DesignationRepository;
import com.hrms.employee.repository.SectionRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.DepartmentService;
import com.hrms.employee.service.OrganisationChanged;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
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
public class DepartmentServiceImpl implements DepartmentService {

    private static final String TARGET = "DEPARTMENT";

    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final SectionRepository sectionRepository;
    private final EmployeeJobDetailsRepository jobDetailsRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditTrail audit;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    @Override
    public List<DepartmentResponse> getAllDepartments() {
        List<Department> all = departmentRepository.findAllByOrderByNameAsc();
        Map<Long, String> heads = headNames(all.stream().map(Department::getHeadEmployeeId).filter(Objects::nonNull).distinct().toList());
        return all.stream().map(d -> toResponse(d, heads)).toList();
    }

    @Override
    public List<DepartmentLookupResponse> getActiveLookup() {
        return departmentRepository.findByIsActiveOrderByNameAsc(true).stream()
            .map(d -> DepartmentLookupResponse.builder()
                .id(d.getDeptId())
                .code(d.getCode())
                .name(d.getName())
                .nameAr(d.getNameAr())
                .build())
            .toList();
    }

    @Override
    public List<ManagerLookupResponse> getManagerCandidates(Long departmentId) {
        findById(departmentId);
        return jobDetailsRepository.findManagerCandidates(departmentId);
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
        if (departmentRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new DuplicateResourceException("Department", "name", request.getName().trim());
        }
        checkHeadEmployee(request.getHeadEmployeeId(), null);

        Department entity = Department.builder().build();
        apply(entity, request, code);
        entity.setIsActive(request.getIsActive() == null || request.getIsActive());

        Department saved = departmentRepository.save(entity);
        // The insert trigger stamps CREATED_AT / UPDATED_AT; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Department created. ID: {}, code: {}", saved.getDeptId(), saved.getCode());
        audit.record("DEPARTMENT_CREATED", TARGET, saved.getDeptId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public DepartmentResponse updateDepartment(Long id, DepartmentRequest request) {
        Department existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a department");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (departmentRepository.existsByCodeIgnoreCaseAndDeptIdNot(code, id)) {
            throw new DuplicateResourceException("Department", "code", code);
        }
        if (departmentRepository.existsByNameIgnoreCaseAndDeptIdNot(request.getName().trim(), id)) {
            throw new DuplicateResourceException("Department", "name", request.getName().trim());
        }
        checkHeadEmployee(request.getHeadEmployeeId(), existing.getHeadEmployeeId());
        if (Boolean.FALSE.equals(request.getIsActive())) checkNoActiveDesignations(existing);

        String before = summary(existing);
        Long headBefore = existing.getHeadEmployeeId();
        Boolean activeBefore = existing.getIsActive();
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        Department saved = departmentRepository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        if (!Objects.equals(headBefore, saved.getHeadEmployeeId()) || !Objects.equals(activeBefore, saved.getIsActive())) {
            events.publishEvent(new OrganisationChanged()); // the head of waiting workflow requests may have changed
        }
        audit.record("DEPARTMENT_UPDATED", TARGET, id, before + " -> " + summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deactivateDepartment(Long id) {
        setActive(id, false);
    }

    @Override
    @Transactional
    public void activateDepartment(Long id) {
        setActive(id, true);
    }

    @Override
    @Transactional
    public void deleteDepartment(Long id) {
        Department dept = findById(id);
        if (designationRepository.existsByDepartmentId(id) || sectionRepository.existsByDepartmentId(id)
                || jobDetailsRepository.existsByDepartmentId(id)) {
            throw inUse(dept);
        }
        departmentRepository.delete(dept);
        try {
            // Flush now so a row added after the check above (foreign key) is reported here, not at commit.
            departmentRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw inUse(dept);
        }
        log.info("Department deleted. ID: {}, code: {}", id, dept.getCode());
        audit.record("DEPARTMENT_DELETED", TARGET, id, summary(dept));
    }

    private BusinessRuleException inUse(Department dept) {
        return new BusinessRuleException("DEPARTMENT_IN_USE",
            "Department " + dept.getCode() + " is used by designations, sections or employee job records. "
                + "Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        Department dept = findById(id);
        if (!active) checkNoActiveDesignations(dept);
        boolean changed = !Objects.equals(dept.getIsActive(), active);
        dept.setIsActive(active);
        dept.setUpdatedAt(LocalDateTime.now());
        departmentRepository.save(dept);
        if (changed) {
            events.publishEvent(new OrganisationChanged()); // an inactive department has no head or secretary
        }
        audit.record(active ? "DEPARTMENT_ACTIVATED" : "DEPARTMENT_DEACTIVATED", TARGET, id, dept.getCode());
    }

    private void checkNoActiveDesignations(Department dept) {
        if (sectionRepository.existsByDepartmentIdAndIsActive(dept.getDeptId(), true)) {
            throw new BusinessRuleException("DEPARTMENT_HAS_ACTIVE_SECTIONS",
                "Department " + dept.getCode() + " still has active sections. Deactivate them first.");
        }
        if (designationRepository.existsByDepartmentIdAndIsActive(dept.getDeptId(), true)) {
            throw new BusinessRuleException("DEPARTMENT_HAS_ACTIVE_DESIGNATIONS",
                "Department " + dept.getCode() + " still has active designations. Deactivate them first.");
        }
    }

    private static String summary(Department d) {
        return "code=" + d.getCode() + ", name=" + d.getName() + ", nameAr=" + d.getNameAr()
            + ", head=" + d.getHeadEmployeeId() + ", costCenter=" + d.getCostCenterCode()
            + ", active=" + d.getIsActive();
    }

    /** A newly named head must be an active employee; an unchanged head is left alone so other edits still save. */
    private void checkHeadEmployee(Long headEmployeeId, Long currentHeadId) {
        if (headEmployeeId == null || headEmployeeId.equals(currentHeadId)) return;
        var head = employeeRepository.findById(headEmployeeId)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", headEmployeeId));
        if (!Boolean.TRUE.equals(head.getIsActive())) {
            throw new BusinessRuleException("HEAD_EMPLOYEE_INACTIVE", "The selected department head is not an active employee.");
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
        d.setDescription(Strings.trimToNull(r.getDescription()));
        d.setHeadEmployeeId(r.getHeadEmployeeId());
        d.setCostCenterCode(Strings.trimToNull(r.getCostCenterCode()));
    }

    private DepartmentResponse toResponse(Department d) {
        Long headId = d.getHeadEmployeeId();
        return toResponse(d, headNames(headId == null ? List.of() : List.of(headId)));
    }

    /** One query for all head names, so the department list does not look each head up separately. */
    private Map<Long, String> headNames(List<Long> employeeIds) {
        if (employeeIds.isEmpty()) return Map.of();
        return employeeRepository.findAllById(employeeIds).stream()
            .collect(Collectors.toMap(e -> e.getId(), e -> e.getFullName()));
    }

    private DepartmentResponse toResponse(Department d, Map<Long, String> headNames) {
        return DepartmentResponse.builder()
            .deptId(d.getDeptId())
            .code(d.getCode())
            .name(d.getName())
            .nameAr(d.getNameAr())
            .description(d.getDescription())
            .directorateId(d.getDirectorateId())
            .headEmployeeId(d.getHeadEmployeeId())
            .headEmployeeName(d.getHeadEmployeeId() == null ? null : headNames.get(d.getHeadEmployeeId()))
            .costCenterCode(d.getCostCenterCode())
            .isActive(Boolean.TRUE.equals(d.getIsActive()))
            .createdAt(d.getCreatedAt() != null ? d.getCreatedAt().toString() : null)
            .updatedAt(d.getUpdatedAt() != null ? d.getUpdatedAt().toString() : null)
            .version(d.getVersion())
            .build();
    }
}
