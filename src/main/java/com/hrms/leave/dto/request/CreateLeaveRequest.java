package com.hrms.leave.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateLeaveRequest {

    @NotBlank(message = "Leave type is required")
    @Size(max = 30)
    private String leaveTypeCode;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotBlank(message = "Reason is required")
    @Size(max = 500)
    private String reason;
}