package com.hrms.payroll.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record EmployeeSalaryResponse(
        Long                              empSalaryId,
        Long                              employeeId,
        String                            employeeCode,
        String                            employeeName,
        String                            designationName,
        String                            departmentName,
        Long                              structureId,
        String                            structureName,
        BigDecimal                        basicSalary,
        BigDecimal                        grossSalary,
        BigDecimal                        netSalary,
        String                            currency,
        LocalDate                         effectiveFrom,
        LocalDate                         effectiveTo,
        Boolean                           isCurrent,
        String                            remarks,
        // Computed salary breakdown for display
        List<SalaryBreakdownItem>         breakdown,
        LocalDateTime                     createdAt,
        LocalDateTime                     updatedAt,
        // Current record only: today's starting scale for the employee's current grade and increment (absent without
        // a grade). When it differs from basicSalary the grade or increment changed and a new salary is due.
        BigDecimal                        payScaleBasic,
        // The allowances of this salary with the amounts it was given (absent when not paid).
        BigDecimal                        socialAmount,
        Long                              qualificationLevelId,
        String                            qualificationLevelName,
        String                            qualificationLevelNameAr,
        BigDecimal                        qualificationAmount,
        // Current record only: what the employee's running loans take this month (absent when none). Deducted from
        // net by the payroll run (step 5), not part of netSalary here.
        BigDecimal                        loanInstalments
) {
    // Inner record for the salary breakdown table
    public record SalaryBreakdownItem(
            String     componentCode,
            String     componentName,
            String     componentNameAr,
            String     componentType,     // EARNING / DEDUCTION / STATUTORY
            BigDecimal amount,
            String     calcBasis          // "Fixed", "25% of Basic", etc.
    ) {}
}
