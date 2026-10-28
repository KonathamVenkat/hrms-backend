package com.hrms.employee.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GradeRequest {

    @NotBlank(message = "Grade code is required")
    @Size(max = 30)
    @Pattern(regexp = "^[A-Z0-9_]+$",
             message = "Code must be uppercase letters, numbers and underscores only")
    private String code;

    @NotBlank(message = "Grade name is required")
    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Name contains control or invisible characters")
    private String name;

    @NotBlank(message = "Arabic grade name is required")
    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic name contains control or invisible characters")
    private String nameAr;

    @Min(0) @Max(9999)
    private Integer sortOrder;

    /** Optional pay range; null clears it. Amounts are 0 or more with at most 3 decimals, and max is not below min. */
    @DecimalMin("0") @Digits(integer = 9, fraction = 3)
    private BigDecimal minSalary;

    @DecimalMin("0") @Digits(integer = 9, fraction = 3)
    private BigDecimal maxSalary;

    private Boolean isActive;

    /** The version the screen was loaded with; omit to skip the stale-edit check. */
    private Long version;
}
