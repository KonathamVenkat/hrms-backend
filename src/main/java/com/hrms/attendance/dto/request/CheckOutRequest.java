package com.hrms.attendance.dto.request;

import com.hrms.attendance.enums.PunchSource;
import jakarta.validation.constraints.NotNull;

public record CheckOutRequest(

        @NotNull(message = "Employee ID is required")
        Long employeeId,


        /** Ignored: the server decides how a punch is recorded. */ PunchSource punchSource,

        @jakarta.validation.constraints.Size(max = 500, message = "Notes max 500 characters")
        String notes
) {}
