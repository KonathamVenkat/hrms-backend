package com.hrms.attendance.dto.request;

import com.hrms.attendance.enums.PunchSource;
import jakarta.validation.constraints.NotNull;

public record CheckOutRequest(

        @NotNull(message = "Employee ID is required")
        Long employeeId,

        String checkOutTime,        // ISO — null = now

        PunchSource punchSource,

        String notes
) {}
