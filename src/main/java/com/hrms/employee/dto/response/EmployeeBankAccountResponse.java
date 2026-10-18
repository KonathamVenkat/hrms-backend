package com.hrms.employee.dto.response;

import lombok.*;

@Data @Builder
public class EmployeeBankAccountResponse {
    private Long    id;
    private Long    employeeId;
    private Long    bankId;
    private String  bankName;
    private String  bankNameAr;
    private Long    bankBranchId;
    private String  bankBranchName;
    private String  bankBranchNameAr;
    /** Last 4 characters only when {@link #masked} is true. */
    private String  accountNumber;
    private String  iban;
    private String  accountHolderName;
    private Boolean isPrimary;
    private Boolean isActive;
    /** True when the numbers above are masked (HR_MANAGER); such a caller cannot edit. */
    private Boolean masked;
    private Long    version;
    private String  createdBy;
    private String  updatedBy;
    private String  createdAt;
    private String  updatedAt;
}
