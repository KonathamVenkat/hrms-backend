package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.RelationRequest;
import com.hrms.employee.dto.response.RelationResponse;
import com.hrms.employee.dto.response.MasterLookupResponse;
import com.hrms.employee.entity.Relation;
import com.hrms.employee.repository.RelationRepository;
import com.hrms.employee.repository.RelationTypeRepository;
import com.hrms.employee.entity.RelationType;
import com.hrms.employee.service.RelationService;
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
public class RelationServiceImpl implements RelationService {

    private static final String TARGET = "RELATION";

    private final RelationRepository repository;
    private final RelationTypeRepository relationTypeRepository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<RelationResponse> getAll() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }


    @Override
    public List<MasterLookupResponse> getActiveLookup() {
        return repository.findByIsActiveOrderByNameAsc(true).stream().map(e -> MasterLookupResponse.builder().id(e.getId()).code(e.getCode()).name(e.getName()).nameAr(e.getNameAr()).build()).toList();
    }

    @Override
    public RelationResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public RelationResponse create(RelationRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Relation", "code", code);
        }
        boolean active = request.getIsActive() == null || request.getIsActive();
        checkParent(request.getRelationTypeId(), active);

        Relation entity = Relation.builder().build();
        apply(entity, request, code);
        entity.setIsActive(active);

        Relation saved = repository.save(entity);
        // CREATED_AT / UPDATED_AT come from the column defaults; read them back so the response carries them.
        entityManager.flush();
        entityManager.refresh(saved);
        log.info("Relation created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("RELATION_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public RelationResponse update(Long id, RelationRequest request) {
        Relation existing = findById(id);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a relation");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String code = request.getCode().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Relation", "code", code);
        }
        boolean active = request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE.equals(existing.getIsActive());
        checkParent(request.getRelationTypeId(), active);

        String before = summary(existing);
        apply(existing, request, code);
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        Relation saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("RELATION_UPDATED", TARGET, id, before + " -> " + summary(saved));
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
        Relation e = findById(id);
        repository.delete(e);
        try {
            // Flush now so a row that refers to this one (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw inUse(e);
        }
        log.info("Relation deleted. ID: {}, code: {}", id, e.getCode());
        audit.record("RELATION_DELETED", TARGET, id, summary(e));
    }

    private BusinessRuleException inUse(Relation e) {
        return new BusinessRuleException("MASTER_IN_USE",
            "Relation " + e.getCode() + " is in use. Deactivate it instead of deleting it.");
    }

    private void setActive(Long id, boolean active) {
        Relation e = findById(id);
        if (active) checkParent(e.getRelationTypeId(), true);
        e.setIsActive(active);
        e.setUpdatedAt(LocalDateTime.now());
        repository.save(e);
        audit.record(active ? "RELATION_ACTIVATED" : "RELATION_DEACTIVATED", TARGET, id, e.getCode());
    }

    /** The relation type must exist, and an active relation needs an active relation type. */
    private void checkParent(Long relationTypeId, boolean childActive) {
        RelationType parent = relationTypeRepository.findById(relationTypeId)
            .orElseThrow(() -> new ResourceNotFoundException("Relation type", "id", relationTypeId));
        if (childActive && !Boolean.TRUE.equals(parent.getIsActive())) {
            throw new BusinessRuleException("PARENT_INACTIVE",
                "Relation type " + parent.getCode() + " is inactive, so it cannot hold an active relation.");
        }
    }

    private static String summary(Relation e) {
        return "code=" + e.getCode() + ", name=" + e.getName() + ", nameAr=" + e.getNameAr() + ", relationTypeId=" + e.getRelationTypeId()
            + ", active=" + e.getIsActive();
    }

    private Relation findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Relation", "id", id));
    }

    private void apply(Relation e, RelationRequest r, String code) {
        e.setRelationTypeId(r.getRelationTypeId());
        e.setCode(code);
        e.setName(r.getName().trim());
        e.setNameAr(r.getNameAr().trim());
    }

    private RelationResponse toResponse(Relation e) {
        return RelationResponse.builder()
            .id(e.getId())
            .relationTypeId(e.getRelationTypeId())
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
