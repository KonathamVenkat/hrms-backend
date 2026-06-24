package com.hrms.leave.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ApproveLeaveRequest {

    @NotBlank(message = "Action is required")
    @Pattern(regexp = "^(APPROVED|REJECTED)$",
             message = "Action must be APPROVED or REJECTED")
    private String action;

    @Size(max = 500)
    private String remarks;
}
