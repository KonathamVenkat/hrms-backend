package com.hrms.leave.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.leave.dto.request.HolidayRequest;
import com.hrms.leave.dto.response.HolidayResponse;
import com.hrms.leave.service.HolidayCalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/holiday-calendar")
@RequiredArgsConstructor
public class HolidayCalendarController {

    private final HolidayCalendarService holidayService;

    /**
     * GET /api/v1/admin/holiday-calendar?year=2026
     * All holidays for a year — admin table view.
     */
    @GetMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<List<HolidayResponse>>> getHolidaysByYear(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}")
            Integer year) {

        log.info("GET /api/v1/admin/holiday-calendar?year={}", year);
        return ResponseEntity.ok(
            ApiResponse.<List<HolidayResponse>>builder()
                .success(true)
                .message("Holidays fetched successfully")
                .data(holidayService.getHolidaysByYear(year))
                .statusCode(200)
                .build()
        );
    }

    /**
     * GET /api/v1/admin/holiday-calendar/active?year=2026
     * Active holidays only — used by leave request dropdown.
     */
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<HolidayResponse>>> getActiveHolidays(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}")
            Integer year) {

        return ResponseEntity.ok(
            ApiResponse.<List<HolidayResponse>>builder()
                .success(true)
                .message("Active holidays fetched")
                .data(holidayService.getActiveHolidaysByYear(year))
                .statusCode(200)
                .build()
        );
    }

    /**
     * GET /api/v1/admin/holiday-calendar/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<HolidayResponse>> getHolidayById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
            ApiResponse.<HolidayResponse>builder()
                .success(true)
                .message("Holiday fetched")
                .data(holidayService.getHolidayById(id))
                .statusCode(200)
                .build()
        );
    }

    /**
     * POST /api/v1/admin/holiday-calendar
     */
    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<HolidayResponse>> createHoliday(
            @Valid @RequestBody HolidayRequest request) {

        log.info("POST /api/v1/admin/holiday-calendar - {}", request.getHolidayName());
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                ApiResponse.<HolidayResponse>builder()
                    .success(true)
                    .message("Holiday created successfully")
                    .data(holidayService.createHoliday(request))
                    .statusCode(201)
                    .build()
            );
    }

    /**
     * PUT /api/v1/admin/holiday-calendar/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<HolidayResponse>> updateHoliday(
            @PathVariable Long id,
            @Valid @RequestBody HolidayRequest request) {

        log.info("PUT /api/v1/admin/holiday-calendar/{}", id);
        return ResponseEntity.ok(
            ApiResponse.<HolidayResponse>builder()
                .success(true)
                .message("Holiday updated successfully")
                .data(holidayService.updateHoliday(id, request))
                .statusCode(200)
                .build()
        );
    }

    /**
     * PATCH /api/v1/admin/holiday-calendar/{id}/deactivate
     */
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateHoliday(@PathVariable Long id) {
        holidayService.deactivateHoliday(id);
        return ResponseEntity.ok(
            ApiResponse.<Void>builder()
                .success(true).message("Holiday deactivated").statusCode(200).build()
        );
    }

    /**
     * PATCH /api/v1/admin/holiday-calendar/{id}/activate
     */
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activateHoliday(@PathVariable Long id) {
        holidayService.activateHoliday(id);
        return ResponseEntity.ok(
            ApiResponse.<Void>builder()
                .success(true).message("Holiday activated").statusCode(200).build()
        );
    }
}
