// ── WorkShiftRequest.java ─────────────────────────────────────
package com.hrms.employee.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WorkShiftRequest {

    @NotBlank(message = "Shift code is required")
    @Size(max = 20, message = "Shift code max 20 characters")
    @Pattern(regexp = "^[A-Z0-9_]+$",
             message = "Code must be uppercase letters, numbers and underscores only")
    private String shiftCode;

    @NotBlank(message = "Shift name is required")
    @Size(max = 100, message = "Shift name max 100 characters")
    private String shiftName;

    @NotBlank(message = "Arabic shift name is required")
    @Size(max = 200, message = "Arabic name max 200 characters")
    private String shiftNameAr;

    @NotBlank(message = "Shift type is required")
    @Pattern(regexp = "^(MORNING|AFTERNOON|EVENING|NIGHT|FLEXIBLE|SPLIT)$",
             message = "Type must be MORNING, AFTERNOON, EVENING, NIGHT, FLEXIBLE or SPLIT")
    private String shiftType;

    @NotBlank(message = "Start time is required")
    @Pattern(regexp = "^([01]\\d|2[0-3]):([0-5]\\d)$",
             message = "Start time must be in HH:MM format (24hr)")
    private String startTime;

    @NotBlank(message = "End time is required")
    @Pattern(regexp = "^([01]\\d|2[0-3]):([0-5]\\d)$",
             message = "End time must be in HH:MM format (24hr)")
    private String endTime;

    @NotNull(message = "Working hours is required")
    @DecimalMin(value = "0.5", message = "Working hours must be at least 0.5")
    @DecimalMax(value = "24",  message = "Working hours cannot exceed 24")
    private BigDecimal workingHours;

    @Min(value = 0,   message = "Break duration cannot be negative")
    @Max(value = 120, message = "Break duration cannot exceed 120 minutes")
    private Integer breakDuration;

    @Min(value = 0,  message = "Grace period cannot be negative")
    @Max(value = 60, message = "Grace period cannot exceed 60 minutes")
    private Integer gracePeriod;

    @NotBlank(message = "Working days is required")
    private String workingDays;     // "SUN,MON,TUE,WED,THU"

    private Boolean isOvernight;
    private Boolean isFlexible;

    @Size(max = 500, message = "Description max 500 characters")
    private String description;

    @Min(value = 0)
    private Integer sortOrder;

    private Boolean isActive;
}


