package com.hrms.employee.service.impl;

import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.WorkShiftRequest;
import com.hrms.employee.dto.response.WorkShiftResponse;
import com.hrms.employee.entity.WorkShift;
import com.hrms.employee.repository.WorkShiftRepository;
import com.hrms.employee.service.WorkShiftService;
import com.hrms.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkShiftServiceImpl implements WorkShiftService {

    private final WorkShiftRepository workShiftRepository;

    // ── Read ──────────────────────────────────────────────────

    @Override
    public List<WorkShiftResponse> getAllShifts() {
        return workShiftRepository.findAllByOrderBySortOrderAsc()
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<WorkShiftResponse> getActiveShifts() {
        return workShiftRepository.findByIsActiveOrderBySortOrderAsc(1)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public WorkShiftResponse getShiftById(Long id) {
        return toResponse(findById(id));
    }

    // ── Create ────────────────────────────────────────────────

    @Override
    @Transactional
    public WorkShiftResponse createShift(WorkShiftRequest request) {
        log.info("Creating work shift: {}", request.getShiftCode());

        if (workShiftRepository.existsByShiftCodeIgnoreCase(request.getShiftCode())) {
            throw new DuplicateResourceException("WorkShift", "shiftCode", request.getShiftCode());
        }
        if (workShiftRepository.existsByShiftNameIgnoreCase(request.getShiftName())) {
            throw new DuplicateResourceException("WorkShift", "shiftName", request.getShiftName());
        }

        validateShiftTiming(request);

        WorkShift entity = buildEntity(request);
        entity.setCreatedBy(getCurrentAuditor());
        entity.setCreatedAt(LocalDateTime.now());

        WorkShift saved = workShiftRepository.save(entity);
        log.info("Work shift created. ID: {}, Code: {}", saved.getShiftId(), saved.getShiftCode());
        return toResponse(saved);
    }

    // ── Update ────────────────────────────────────────────────

    @Override
    @Transactional
    public WorkShiftResponse updateShift(Long id, WorkShiftRequest request) {
        log.info("Updating work shift id: {}", id);

        WorkShift existing = findById(id);

        validateShiftTiming(request);

        if (workShiftRepository.existsByShiftCodeIgnoreCaseAndShiftIdNot(
                request.getShiftCode(), id)) {
            throw new DuplicateResourceException("WorkShift", "shiftCode", request.getShiftCode());
        }
        if (workShiftRepository.existsByShiftNameIgnoreCaseAndShiftIdNot(
                request.getShiftName(), id)) {
            throw new DuplicateResourceException("WorkShift", "shiftName", request.getShiftName());
        }

        existing.setShiftCode(request.getShiftCode().toUpperCase().trim());
        existing.setShiftName(request.getShiftName().trim());
        existing.setShiftNameAr(request.getShiftNameAr().trim());
        existing.setShiftType(request.getShiftType().toUpperCase());
        existing.setStartTime(request.getStartTime());
        existing.setEndTime(request.getEndTime());
        existing.setBreakDuration(request.getBreakDuration() != null ? request.getBreakDuration() : 0);
        existing.setWorkingHours(request.getWorkingHours());
        existing.setGracePeriod(request.getGracePeriod() != null ? request.getGracePeriod() : 0);
        existing.setWorkingDays(request.getWorkingDays().toUpperCase().trim());
        existing.setIsOvernight(boolToInt(request.getIsOvernight()));
        existing.setIsFlexible(boolToInt(request.getIsFlexible()));
        existing.setDescription(request.getDescription());
        existing.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        if (request.getIsActive() != null) {
            existing.setIsActive(boolToInt(request.getIsActive()));
        }
        existing.setUpdatedBy(getCurrentAuditor());
        existing.setUpdatedAt(LocalDateTime.now());

        return toResponse(workShiftRepository.save(existing));
    }

    // ── Activate / Deactivate ─────────────────────────────────

    @Override
    @Transactional
    public void deactivateShift(Long id) {
        WorkShift ws = findById(id);
        ws.setIsActive(0);
        ws.setUpdatedBy(getCurrentAuditor());
        ws.setUpdatedAt(LocalDateTime.now());
        workShiftRepository.save(ws);
        log.info("Work shift deactivated. ID: {}", id);
    }

    @Override
    @Transactional
    public void activateShift(Long id) {
        WorkShift ws = workShiftRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("WorkShift", "id", id));
        ws.setIsActive(1);
        ws.setUpdatedBy(getCurrentAuditor());
        ws.setUpdatedAt(LocalDateTime.now());
        workShiftRepository.save(ws);
        log.info("Work shift activated. ID: {}", id);
    }

    // ── Private helpers ───────────────────────────────────────

    private WorkShift findById(Long id) {
        return workShiftRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("WorkShift", "id", id));
    }

    private String getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return "SYSTEM";
        return auth.getName();
    }

    /**
     * Cross-checks isOvernight against the actual start/end order, and workingHours
     * against the span those times + breakDuration actually produce (5-minute tolerance
     * for rounding). These feed attendance/payroll downstream, so a mismatch here would
     * silently corrupt hours-worked and overtime calculations rather than fail loudly.
     */
    private void validateShiftTiming(WorkShiftRequest request) {
        LocalTime start = LocalTime.parse(request.getStartTime());
        LocalTime end = LocalTime.parse(request.getEndTime());
        boolean overnight = Boolean.TRUE.equals(request.getIsOvernight());

        if (start.equals(end)) {
            throw new BusinessRuleException("INVALID_SHIFT_TIMES",
                "Start time and end time cannot be the same.");
        }
        boolean crossesMidnight = end.isBefore(start);
        if (overnight != crossesMidnight) {
            throw new BusinessRuleException("INVALID_SHIFT_TIMES",
                crossesMidnight
                    ? "End time is before start time — mark this shift as overnight."
                    : "End time is after start time — this shift doesn't cross midnight, uncheck overnight.");
        }

        long spanMinutes = crossesMidnight
            ? (24 * 60 - start.toSecondOfDay() / 60) + end.toSecondOfDay() / 60
            : (end.toSecondOfDay() - start.toSecondOfDay()) / 60;
        int breakMinutes = request.getBreakDuration() != null ? request.getBreakDuration() : 0;
        long expectedWorkingMinutes = spanMinutes - breakMinutes;

        long actualWorkingMinutes = Math.round(request.getWorkingHours().doubleValue() * 60);
        if (Math.abs(expectedWorkingMinutes - actualWorkingMinutes) > 5) {
            throw new BusinessRuleException("INVALID_SHIFT_TIMES",
                String.format(
                    "Working hours (%.2f) don't match start/end time minus break (expected ~%.2f hours).",
                    request.getWorkingHours().doubleValue(), expectedWorkingMinutes / 60.0));
        }
    }

    private WorkShift buildEntity(WorkShiftRequest req) {
        return WorkShift.builder()
            .shiftCode(req.getShiftCode().toUpperCase().trim())
            .shiftName(req.getShiftName().trim())
            .shiftNameAr(req.getShiftNameAr().trim())
            .shiftType(req.getShiftType().toUpperCase())
            .startTime(req.getStartTime())
            .endTime(req.getEndTime())
            .breakDuration(req.getBreakDuration() != null ? req.getBreakDuration() : 0)
            .workingHours(req.getWorkingHours())
            .gracePeriod(req.getGracePeriod() != null ? req.getGracePeriod() : 0)
            .workingDays(req.getWorkingDays().toUpperCase().trim())
            .isOvernight(boolToInt(req.getIsOvernight()))
            .isFlexible(boolToInt(req.getIsFlexible()))
            .description(req.getDescription())
            .isActive(req.getIsActive() != null ? boolToInt(req.getIsActive()) : 1)
            .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
            .build();
    }

    private WorkShiftResponse toResponse(WorkShift ws) {
        return WorkShiftResponse.builder()
            .shiftId(ws.getShiftId())
            .shiftCode(ws.getShiftCode())
            .shiftName(ws.getShiftName())
            .shiftNameAr(ws.getShiftNameAr())
            .shiftType(ws.getShiftType())
            .startTime(ws.getStartTime())
            .endTime(ws.getEndTime())
            .breakDuration(ws.getBreakDuration())
            .workingHours(ws.getWorkingHours())
            .gracePeriod(ws.getGracePeriod())
            .workingDays(ws.getWorkingDays())
            .isOvernight(ws.getIsOvernight() == 1)
            .isFlexible(ws.getIsFlexible() == 1)
            .description(ws.getDescription())
            .isActive(ws.getIsActive() == 1)
            .sortOrder(ws.getSortOrder())
            .createdBy(ws.getCreatedBy())
            .createdAt(ws.getCreatedAt() != null ? ws.getCreatedAt().toString() : null)
            .updatedAt(ws.getUpdatedAt() != null ? ws.getUpdatedAt().toString() : null)
            .build();
    }

    private int boolToInt(Boolean b) {
        return Boolean.TRUE.equals(b) ? 1 : 0;
    }
}
