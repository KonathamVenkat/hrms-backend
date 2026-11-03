package com.hrms.employee.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BankBranchRequest {

    @NotNull(message = "Bank is required")
    private Long bankId;

    @NotBlank(message = "Bank branch code is required")
    @Size(max = 30)
    @Pattern(regexp = "^[A-Z0-9_]+$",
             message = "Code must be uppercase letters, numbers and underscores only")
    private String code;

    @NotBlank(message = "Bank branch name is required")
    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Name contains control or invisible characters")
    private String name;

    @NotBlank(message = "Arabic bank branch name is required")
    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Arabic name contains control or invisible characters")
    private String nameAr;

    @Size(max = 300, message = "Address max 300 characters")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Address contains control or invisible characters")
    private String address;

    @Size(max = 30, message = "Phone max 30 characters")
    @Pattern(regexp = "^[0-9+()\\- ]*$", message = "Phone may only hold digits, spaces and + ( ) -")
    private String phone;

    @Size(max = 200, message = "Email max 200 characters")
    @Email(message = "Email must be a valid address")
    private String email;

    private Boolean isActive;

    /** The version the screen was loaded with; omit to skip the stale-edit check. */
    private Long version;
}
