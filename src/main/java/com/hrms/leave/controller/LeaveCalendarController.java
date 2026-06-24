package com.hrms.leave.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.leave.dto.response.CalendarMonthResponse;
import com.hrms.leave.service.LeaveCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/leave/calendar")
@RequiredArgsConstructor
public class LeaveCalendarController {

    private final LeaveCalendarService calendarService;

    /**
     * GET /api/v1/leave/calendar?year=2026&month=6
     * Returns full calendar data for the given month.
     * Defaults to current month if year/month not provided.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER','EMPLOYEE')")
    public ResponseEntity<ApiResponse<CalendarMonthResponse>> getCalendar(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        CalendarMonthResponse data = (year != null && month != null)
            ? calendarService.getMonthCalendar(year, month)
            : calendarService.getCurrentMonthCalendar();

        log.info("GET calendar — year={} month={}", year, month);

        return ResponseEntity.ok(
            ApiResponse.<CalendarMonthResponse>builder()
                .success(true)
                .message("Calendar fetched successfully")
                .data(data)
                .statusCode(200)
                .build());
    }
}
