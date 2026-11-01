package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.SectionRequest;
import com.hrms.employee.dto.response.SectionResponse;
import com.hrms.employee.entity.Section;
import com.hrms.employee.repository.SectionRepository;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.dto.response.SectionLookupResponse;
import com.hrms.employee.entity.Department;
import com.hrms.employee.service.SectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SectionServiceImpl implements SectionService {

    private static final String TARGET = "SECTION";

    private final SectionRepository repository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeJobDetailsRepository jobDetailsRepository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<SectionResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public List<SectionLookupResponse> getActiveLookupByDepartment(Long departmentId) {
        return repository.findByDepartmentIdAndIsActiveOrderByNameAsc(departmentId, true).stream()
            .map(e -> SectionLookupResponse.builder().id(e.getId()).code(e.getCode())
                .name(e.getName()).nameAr(e.getNameAr()).departmentId(e.getDepartmentId()).build())
            .toList();
    }

    @Override
    public SectionResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public SectionResponse create(SectionRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Section", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();
        checkParent(request.getDepartmentId(), active);

        Section entity = Section.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        Section saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Section created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("SECTION_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public SectionResponse update(Long id, SectionRequest request) {
        Section existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a section");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Section", "code", code);
        }
        boolean active = request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE.equals(existing.getIsActive());
        checkParent(request.getDepartmentId(), active);
        if (!existing.getDepartmentId().equals(request.getDepartmentId()) && jobDetailsRepository.existsBySectionId(id)) {
            throw new BusinessRuleException("SECTION_IN_USE",
                "Section " + existing.getCode() + " is used by employee job records, so it cannot move to another department.");
        }

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        Section saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("SECTION_UPDATED", TARGET, id, before + " -> " + summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        setActive(id, false);
    }

    @Override
    @Transactional
    public void activate(Long id) {
        setActive(id, true);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Section e = findById(id);
        if (jobDetailsRepository.existsBySectionId(id)) throw inUse(e);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("Section deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("SECTION_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(Section e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "Section " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        Section e = findById(id);
        if (active) checkParent(e.getDepartmentId(), true);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "SECTION_ACTIVATED" : "SECTION_DEACTIVATED", TARGET, id, e.getCode());
    }

    /** The department must exist, and an active section needs an active department. */
    private void checkParent(Long departmentId, boolean childActive) {
        Department parent = departmentRepository.findById(departmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Department", "id", departmentId));
        if (childActive && !Boolean.TRUE.equals(parent.getIsActive())) {
            throw new BusinessRuleException("PARENT_INACTIVE",
                "Department " + parent.getCode() + " is inactive, so it cannot hold an active section.");
        }
    }

    private static String summary(Section e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr() + ", departmentId=" + e.getDepartmentId()
            + ", active=" + e.getIsActive();
    }

    private Section findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Section", "id", id));
    }

    private void apply(Section e, SectionRequest r, String code) {
        e.setDepartmentId(r.getDepartmentId());
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private SectionResponse toResponse(Section e) {
        return SectionResponse.builder()
            .id(e.getId())
            .departmentId(e.getDepartmentId())
            .code(e.getCode())
            .name(e.getName())
            .nameAr(e.getNameAr())
            .isActive(Boolean.TRUE.equals(e.getIsActive()))
            .createdAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
            .updatedAt(e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null)
            .version(e.getVersion())
            .build();
    }
}
