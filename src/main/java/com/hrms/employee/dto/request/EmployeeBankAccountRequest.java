package com.hrms.employee.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmployeeBankAccountRequest {

    @NotNull(message = "Bank is required")
    private Long bankId;

    /** Optional; must belong to the chosen bank. */
    private Long bankBranchId;

    /** Letters, digits, spaces, hyphens and slashes; stored upper-case with spaces removed. */
    @NotBlank(message = "Account number is required")
    @Size(max = 40)
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 /-]*$",
             message = "Account number may contain only letters, digits, spaces, hyphens and slashes")
    private String accountNumber;

    /** Optional; when given it must be a valid IBAN (format and check digits). */
    @Size(max = 50)
    @Pattern(regexp = "^[A-Za-z0-9 ]*$", message = "IBAN may contain only letters, digits and spaces")
    private String iban;

    @Size(max = 200)
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Account holder name contains control or invisible characters")
    private String accountHolderName;

    private Boolean isPrimary;

    /** The version the screen was loaded with. Required to update; ignored when adding. */
    private Long version;
}
