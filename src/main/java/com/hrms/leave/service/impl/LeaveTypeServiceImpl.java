package com.hrms.leave.service.impl;

import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.leave.dto.request.LeaveTypeRequest;
import com.hrms.leave.dto.response.LeaveTypeResponse;
import com.hrms.leave.entity.LeaveType;
import com.hrms.leave.repository.LeaveTypeRepository;
import com.hrms.leave.service.LeaveTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
 
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveTypeServiceImpl implements LeaveTypeService {
 
    private final LeaveTypeRepository leaveTypeRepository;
 
    // ── Read ──────────────────────────────────────────────────
 
    @Override
    public List<LeaveTypeResponse> getAllLeaveTypes() {
        return leaveTypeRepository.findAllByOrderBySortOrderAsc()
            .stream().map(this::toResponse).collect(Collectors.toList());
    }
 
    @Override
    public List<LeaveTypeResponse> getActiveLeaveTypes() {
        return leaveTypeRepository.findByIsActiveOrderBySortOrderAsc(1)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }
 
    @Override
    public LeaveTypeResponse getLeaveTypeById(Long id) {
        return toResponse(findById(id));
    }
 
    // ── Create ────────────────────────────────────────────────
 
    @Override
    @Transactional
    public LeaveTypeResponse createLeaveType(LeaveTypeRequest request) {
        log.info("Creating leave type: {}", request.getCode());
 
        if (leaveTypeRepository.existsByCodeIgnoreCase(request.getCode())) {
            throw new DuplicateResourceException("LeaveType", "code", request.getCode());
        }
        if (leaveTypeRepository.existsByNameEnIgnoreCase(request.getNameEn())) {
            throw new DuplicateResourceException("LeaveType", "nameEn", request.getNameEn());
        }
 
        LeaveType entity = buildEntity(request, null);
        entity.setCreatedBy(getCurrentAuditor());
        entity.setCreatedAt(LocalDateTime.now());
 
        LeaveType saved = leaveTypeRepository.save(entity);
        log.info("Leave type created. ID: {}, Code: {}", saved.getLeaveTypeId(), saved.getCode());
        return toResponse(saved);
    }
 
    // ── Update ────────────────────────────────────────────────
 
    @Override
    @Transactional
    public LeaveTypeResponse updateLeaveType(Long id, LeaveTypeRequest request) {
        log.info("Updating leave type id: {}", id);
 
        LeaveType existing = findById(id);
 
        if (leaveTypeRepository.existsByCodeIgnoreCaseAndLeaveTypeIdNot(
                request.getCode(), id)) {
            throw new DuplicateResourceException("LeaveType", "code", request.getCode());
        }
        if (leaveTypeRepository.existsByNameEnIgnoreCaseAndLeaveTypeIdNot(
                request.getNameEn(), id)) {
            throw new DuplicateResourceException("LeaveType", "nameEn", request.getNameEn());
        }
 
        existing.setCode(request.getCode().toUpperCase().trim());
        existing.setNameEn(request.getNameEn().trim());
        existing.setNameAr(request.getNameAr().trim());
        existing.setDescription(request.getDescription());
        existing.setDefaultDays(
            request.getDefaultDays() != null ? request.getDefaultDays() : BigDecimal.ZERO);
        existing.setIsPaid(boolToInt(request.getIsPaid()));
        existing.setIsCarryForward(boolToInt(request.getIsCarryForward()));
        existing.setMaxCarryDays(
            request.getMaxCarryDays() != null ? request.getMaxCarryDays() : BigDecimal.ZERO);
        existing.setRequiresDocument(boolToInt(request.getRequiresDocument()));
        existing.setDocMaxFileSizeMb(request.getDocMaxFileSizeMb());
        existing.setDocAllowedExtensions(normalizeExtensions(request.getDocAllowedExtensions()));
        existing.setMinNoticeDays(
            request.getMinNoticeDays() != null ? request.getMinNoticeDays() : 0);
        existing.setMaxConsecutiveDays(
            request.getMaxConsecutiveDays() != null ? request.getMaxConsecutiveDays() : 0);
        existing.setApplicableGender(request.getApplicableGender().toUpperCase());
        existing.setSortOrder(
            request.getSortOrder() != null ? request.getSortOrder() : 0);
        if (request.getIsActive() != null) {
            existing.setIsActive(boolToInt(request.getIsActive()));
        }
        existing.setUpdatedBy(getCurrentAuditor());
        existing.setUpdatedAt(LocalDateTime.now());

        return toResponse(leaveTypeRepository.save(existing));
    }
 
    // ── Activate / Deactivate ─────────────────────────────────
 
    @Override
    @Transactional
    public void deactivateLeaveType(Long id) {
        log.info("Deactivating leave type id: {}", id);
        LeaveType lt = findById(id);
        lt.setIsActive(0);
        lt.setUpdatedBy(getCurrentAuditor());
        lt.setUpdatedAt(LocalDateTime.now());
        leaveTypeRepository.save(lt);
    }

    @Override
    @Transactional
    public void activateLeaveType(Long id) {
        log.info("Activating leave type id: {}", id);
        LeaveType lt = leaveTypeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("LeaveType", "id", id));
        lt.setIsActive(1);
        lt.setUpdatedBy(getCurrentAuditor());
        lt.setUpdatedAt(LocalDateTime.now());
        leaveTypeRepository.save(lt);
    }
 
    // ── Private helpers ───────────────────────────────────────
 
    private LeaveType findById(Long id) {
        return leaveTypeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("LeaveType", "id", id));
    }

    /** "PDF, jpg ,PDF" -> "pdf,jpg"; blank -> null (meaning: use the defaults). */
    private String normalizeExtensions(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return java.util.Arrays.stream(raw.split(","))
            .map(s -> s.trim().toLowerCase())
            .filter(s -> !s.isEmpty())
            .distinct()
            .collect(java.util.stream.Collectors.joining(","));
    }

    private String getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return "SYSTEM";
        return auth.getName();
    }
 
    private LeaveType buildEntity(LeaveTypeRequest req, Long id) {
        return LeaveType.builder()
            .leaveTypeId(id)
            .code(req.getCode().toUpperCase().trim())
            .nameEn(req.getNameEn().trim())
            .nameAr(req.getNameAr().trim())
            .description(req.getDescription())
            .defaultDays(req.getDefaultDays() != null
                ? req.getDefaultDays() : BigDecimal.ZERO)
            .isPaid(boolToInt(req.getIsPaid()))
            .isCarryForward(boolToInt(req.getIsCarryForward()))
            .maxCarryDays(req.getMaxCarryDays() != null
                ? req.getMaxCarryDays() : BigDecimal.ZERO)
            .requiresDocument(boolToInt(req.getRequiresDocument()))
            .docMaxFileSizeMb(req.getDocMaxFileSizeMb())
            .docAllowedExtensions(normalizeExtensions(req.getDocAllowedExtensions()))
            .minNoticeDays(req.getMinNoticeDays() != null
                ? req.getMinNoticeDays() : 0)
            .maxConsecutiveDays(req.getMaxConsecutiveDays() != null
                ? req.getMaxConsecutiveDays() : 0)
            .applicableGender(req.getApplicableGender().toUpperCase())
            .isActive(req.getIsActive() != null ? boolToInt(req.getIsActive()) : 1)
            .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
            .build();
    }
 
    private LeaveTypeResponse toResponse(LeaveType lt) {
        return LeaveTypeResponse.builder()
            .leaveTypeId(lt.getLeaveTypeId())
            .code(lt.getCode())
            .nameEn(lt.getNameEn())
            .nameAr(lt.getNameAr())
            .description(lt.getDescription())
            .defaultDays(lt.getDefaultDays())
            .isPaid(lt.getIsPaid() == 1)
            .isCarryForward(lt.getIsCarryForward() == 1)
            .maxCarryDays(lt.getMaxCarryDays())
            .requiresDocument(lt.getRequiresDocument() == 1)
            .docMaxFileSizeMb(lt.resolveDocMaxFileSizeMb())
            .docAllowedExtensions(lt.resolveDocAllowedExtensions())
            .minNoticeDays(lt.getMinNoticeDays())
            .maxConsecutiveDays(lt.getMaxConsecutiveDays())
            .applicableGender(lt.getApplicableGender())
            .isActive(lt.getIsActive() == 1)
            .sortOrder(lt.getSortOrder())
            .createdBy(lt.getCreatedBy())
            .createdAt(lt.getCreatedAt() != null ? lt.getCreatedAt().toString() : null)
            .updatedBy(lt.getUpdatedBy())
            .updatedAt(lt.getUpdatedAt() != null ? lt.getUpdatedAt().toString() : null)
            .build();
    }
 
    private int boolToInt(Boolean b) {
        return Boolean.TRUE.equals(b) ? 1 : 0;
    }
    
}