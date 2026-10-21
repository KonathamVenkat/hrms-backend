package com.hrms.recruitment.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.recruitment.dto.request.StageRequest;
import com.hrms.recruitment.dto.response.RecruitmentLookupResponse;
import com.hrms.recruitment.dto.response.StageResponse;
import com.hrms.recruitment.entity.RecruitmentStage;
import com.hrms.recruitment.repository.RecruitmentStageRepository;
import com.hrms.recruitment.service.StageService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Pipeline stages. The rules protect what the hiring code relies on: system stages stay as they are, a stage keeps its
 * type for life (so a moved application never changes meaning), and exactly one active stage exists per terminal type.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StageServiceImpl implements StageService {

    private static final String TARGET = "RECRUITMENT_STAGE";

    private final RecruitmentStageRepository repository;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<StageResponse> getAll() {
        return repository.findAllByOrderBySortOrderAscNameAsc().stream().map(this::toResponse).toList();
    }

    @Override
    public List<RecruitmentLookupResponse> getActiveLookup() {
        return repository.findByIsActiveOrderBySortOrderAscNameAsc(true).stream()
            .map(s -> RecruitmentLookupResponse.builder()
                .id(s.getId()).code(s.getCode()).name(s.getName()).nameAr(s.getNameAr())
                .kind(s.getStageType().name()).build())
            .toList();
    }

    @Override
    public StageResponse getById(Long id) {
        return toResponse(find(id));
    }

    @Override
    @Transactional
    public StageResponse create(StageRequest request) {
        String code = request.code().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Stage", "code", code);
        }
        boolean active = request.isActive() == null || request.isActive();
        if (active) checkTerminalFree(request.stageType());

        RecruitmentStage entity = RecruitmentStage.builder().isSystem(false).stageType(request.stageType()).build();
        apply(entity, request, code);
        entity.setIsActive(active);

        RecruitmentStage saved = repository.save(entity);
        entityManager.flush();
        entityManager.refresh(saved); // CREATED_AT / UPDATED_AT come from the column defaults
        log.info("Recruitment stage created. ID: {}, code: {}", saved.getId(), saved.getCode());
        audit.record("RECRUITMENT_STAGE_CREATED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public StageResponse update(Long id, StageRequest request) {
        RecruitmentStage existing = find(id);
        if (request.version() == null) throw new BadRequestException("version is required to update a stage");
        requireCurrent(request.version(), existing.getVersion());
        String code = request.code().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("Stage", "code", code);
        }
        if (request.stageType() != existing.getStageType()) {
            throw new BusinessRuleException("STAGE_TYPE_LOCKED",
                "A stage keeps its type. Create a new stage instead of changing " + existing.getCode() + ".");
        }
        if (Boolean.TRUE.equals(existing.getIsSystem())) {
            if (!code.equals(existing.getCode()) || Boolean.FALSE.equals(request.isActive())) {
                throw systemProtected(existing);
            }
        }
        boolean active = request.isActive() != null ? request.isActive() : Boolean.TRUE.equals(existing.getIsActive());
        if (active && !Boolean.TRUE.equals(existing.getIsActive())) checkTerminalFree(existing.getStageType());

        String before = summary(existing);
        apply(existing, request, code);
        existing.setIsActive(active);
        existing.setUpdatedAt(LocalDateTime.now());
        RecruitmentStage saved = repository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("RECRUITMENT_STAGE_UPDATED", TARGET, id, before + " -> " + summary(saved));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        RecruitmentStage s = find(id);
        if (Boolean.TRUE.equals(s.getIsSystem())) throw systemProtected(s);
        s.setIsActive(false);
        s.setUpdatedAt(LocalDateTime.now());
        repository.save(s);
        audit.record("RECRUITMENT_STAGE_DEACTIVATED", TARGET, id, s.getCode());
    }

    @Override
    @Transactional
    public void activate(Long id) {
        RecruitmentStage s = find(id);
        if (!Boolean.TRUE.equals(s.getIsActive())) checkTerminalFree(s.getStageType());
        s.setIsActive(true);
        s.setUpdatedAt(LocalDateTime.now());
        repository.save(s);
        audit.record("RECRUITMENT_STAGE_ACTIVATED", TARGET, id, s.getCode());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        RecruitmentStage s = find(id);
        if (Boolean.TRUE.equals(s.getIsSystem())) throw systemProtected(s);
        repository.delete(s);
        try {
            // Flush now so an application or history row that uses this stage (foreign key) is reported here, not at commit.
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("MASTER_IN_USE",
                "Stage " + s.getCode() + " is in use. Deactivate it instead of deleting it.");
        }
        log.info("Recruitment stage deleted. ID: {}, code: {}", id, s.getCode());
        audit.record("RECRUITMENT_STAGE_DELETED", TARGET, id, summary(s));
    }

    /** A terminal type (hired, rejected, withdrawn) has exactly one active stage; a second one would be ambiguous. */
    private void checkTerminalFree(com.hrms.recruitment.enums.StageType type) {
        if (type.isTerminal() && repository.existsByStageTypeAndIsActive(type, true)) {
            throw new BusinessRuleException("STAGE_TERMINAL_EXISTS",
                "There is already an active " + type + " stage. Deactivate it first.");
        }
    }

    private static BusinessRuleException systemProtected(RecruitmentStage s) {
        return new BusinessRuleException("STAGE_SYSTEM_PROTECTED",
            "Stage " + s.getCode() + " is used by the hiring flow, so it cannot be re-coded, deactivated or deleted.");
    }

    private static void requireCurrent(Long sent, Long current) {
        if (!Objects.equals(sent, current)) {
            throw new OptimisticLockingFailureException("Version " + sent + " is stale, current is " + current);
        }
    }

    private RecruitmentStage find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Stage", "id", id));
    }

    private static void apply(RecruitmentStage s, StageRequest r, String code) {
        s.setCode(code);
        s.setName(r.name().trim());
        s.setNameAr(r.nameAr().trim());
        s.setSortOrder(r.sortOrder());
    }

    private static String summary(RecruitmentStage s) {
        return "code=" + s.getCode() + ", name=" + s.getName() + ", type=" + s.getStageType()
            + ", sort=" + s.getSortOrder() + ", system=" + s.getIsSystem() + ", active=" + s.getIsActive();
    }

    private StageResponse toResponse(RecruitmentStage s) {
        return StageResponse.builder()
            .id(s.getId()).code(s.getCode()).name(s.getName()).nameAr(s.getNameAr())
            .stageType(s.getStageType()).sortOrder(s.getSortOrder())
            .isSystem(Boolean.TRUE.equals(s.getIsSystem())).isActive(Boolean.TRUE.equals(s.getIsActive()))
            .createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt()).version(s.getVersion())
            .build();
    }
}
