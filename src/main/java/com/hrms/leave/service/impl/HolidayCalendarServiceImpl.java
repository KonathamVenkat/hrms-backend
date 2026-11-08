package com.hrms.leave.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.employee.service.impl.Versions;
import com.hrms.common.audit.CurrentAuditor;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.leave.dto.request.HolidayRequest;
import com.hrms.leave.dto.response.HolidayResponse;
import com.hrms.leave.entity.HolidayCalendar;
import com.hrms.leave.repository.HolidayCalendarRepository;
import com.hrms.leave.service.HolidayCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HolidayCalendarServiceImpl implements HolidayCalendarService {

    private final HolidayCalendarRepository holidayRepository;
    private final AuditTrail audit;

    private static final String TARGET = "HOLIDAY";

    // ── Read ──────────────────────────────────────────────────

    @Override
    public List<HolidayResponse> getHolidaysByYear(Integer year) {
        return holidayRepository
            .findByYearOrderByHolidayDateAsc(year)
            .stream().map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Override
    public List<HolidayResponse> getActiveHolidaysByYear(Integer year) {
        return holidayRepository
            .findByYearAndIsActiveOrderByHolidayDateAsc(year, 1)
            .stream().map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Override
    public HolidayResponse getHolidayById(Long id) {
        return toResponse(findById(id));
    }

    // ── Create ────────────────────────────────────────────────

    @Override
    @Transactional
    public HolidayResponse createHoliday(HolidayRequest request) {
        log.info("Creating holiday: {} on {}", request.getHolidayName(), request.getHolidayDate());

        if (holidayRepository.existsByHolidayDateAndHolidayNameIgnoreCase(
                request.getHolidayDate(), request.getHolidayName())) {
            throw new DuplicateResourceException(
                "Holiday", "date+name",
                request.getHolidayDate() + " / " + request.getHolidayName());
        }

        HolidayCalendar entity = HolidayCalendar.builder()
            .holidayName(request.getHolidayName().trim())
            .holidayNameAr(request.getHolidayNameAr().trim())
            .holidayDate(request.getHolidayDate())
            .holidayType(request.getHolidayType().toUpperCase())
            .description(request.getDescription())
            .isRecurring(boolToInt(request.getIsRecurring()))
            .year(request.getHolidayDate().getYear())   // also set by DB trigger
            .isActive(request.getIsActive() != null ? boolToInt(request.getIsActive()) : 1)
            .createdBy(CurrentAuditor.name())
            .createdAt(LocalDateTime.now())
            .build();

        HolidayCalendar saved = holidayRepository.saveAndFlush(entity);
        log.info("Holiday created. ID: {}", saved.getHolidayId());
        audit.record("HOLIDAY_CREATED", TARGET, saved.getHolidayId(), summary(saved));
        return toResponse(saved);
    }

    // ── Update ────────────────────────────────────────────────

    @Override
    @Transactional
    public HolidayResponse updateHoliday(Long id, HolidayRequest request) {
        log.info("Updating holiday id: {}", id);

        HolidayCalendar existing = findById(id);
        if (request.getVersion() == null) {
            throw new BadRequestException("version is required to update a holiday");
        }
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        String before = summary(existing);

        if (holidayRepository.existsByHolidayDateAndHolidayNameIgnoreCaseAndHolidayIdNot(
                request.getHolidayDate(), request.getHolidayName(), id)) {
            throw new DuplicateResourceException(
                "Holiday", "date+name",
                request.getHolidayDate() + " / " + request.getHolidayName());
        }

        existing.setHolidayName(request.getHolidayName().trim());
        existing.setHolidayNameAr(request.getHolidayNameAr().trim());
        existing.setHolidayDate(request.getHolidayDate());
        existing.setHolidayType(request.getHolidayType().toUpperCase());
        existing.setDescription(request.getDescription());
        existing.setIsRecurring(boolToInt(request.getIsRecurring()));
        existing.setYear(request.getHolidayDate().getYear());
        if (request.getIsActive() != null) {
            existing.setIsActive(boolToInt(request.getIsActive()));
        }
        existing.setUpdatedBy(CurrentAuditor.name());
        existing.setUpdatedAt(LocalDateTime.now());

        HolidayCalendar saved = holidayRepository.saveAndFlush(existing); // so the response carries the new version
        audit.record("HOLIDAY_UPDATED", TARGET, id, before + " -> " + summary(saved));
        return toResponse(saved);
    }

    // ── Activate / Deactivate ─────────────────────────────────

    @Override
    @Transactional
    public void deactivateHoliday(Long id) {
        HolidayCalendar h = findById(id);
        h.setIsActive(0);
        h.setUpdatedBy(CurrentAuditor.name());
        h.setUpdatedAt(LocalDateTime.now());
        holidayRepository.save(h);
        log.info("Holiday deactivated. ID: {}", id);
        audit.record("HOLIDAY_DEACTIVATED", TARGET, id, h.getHolidayName());
    }

    @Override
    @Transactional
    public void activateHoliday(Long id) {
        HolidayCalendar h = holidayRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Holiday", "id", id));
        h.setIsActive(1);
        h.setUpdatedBy(CurrentAuditor.name());
        h.setUpdatedAt(LocalDateTime.now());
        holidayRepository.save(h);
        log.info("Holiday activated. ID: {}", id);
        audit.record("HOLIDAY_ACTIVATED", TARGET, id, h.getHolidayName());
    }

    // ── Private helpers ───────────────────────────────────────

    private static String summary(HolidayCalendar h) {
        return "name=" + h.getHolidayName() + ", date=" + h.getHolidayDate() + ", type=" + h.getHolidayType()
            + ", recurring=" + h.getIsRecurring() + ", active=" + h.getIsActive();
    }

    private HolidayCalendar findById(Long id) {
        return holidayRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Holiday", "id", id));
    }

    private HolidayResponse toResponse(HolidayCalendar h) {
        return HolidayResponse.builder()
            .holidayId(h.getHolidayId())
            .holidayName(h.getHolidayName())
            .holidayNameAr(h.getHolidayNameAr())
            .holidayDate(h.getHolidayDate() != null
                ? h.getHolidayDate().toString() : null)
            .holidayType(h.getHolidayType())
            .description(h.getDescription())
            .isRecurring(h.getIsRecurring() == 1)
            .year(h.getYear())
            .isActive(h.getIsActive() == 1)
            .createdAt(h.getCreatedAt() != null
                ? h.getCreatedAt().toString() : null)
            .updatedAt(h.getUpdatedAt() != null
                ? h.getUpdatedAt().toString() : null)
            .version(h.getVersion())
            .build();
    }

    private int boolToInt(Boolean b) {
        return Boolean.TRUE.equals(b) ? 1 : 0;
    }
}
