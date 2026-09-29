package com.hrms.attendance.dto.request;

import com.hrms.attendance.enums.PunchSource;
import jakarta.validation.constraints.NotNull;

// ── Check-In ─────────────────────────────────────────────────
public record CheckInRequest(

        @NotNull(message = "Employee ID is required")
        Long employeeId,


        PunchSource punchSource,    // null defaults to WEB

        Long locationId,

        String notes
) {}
