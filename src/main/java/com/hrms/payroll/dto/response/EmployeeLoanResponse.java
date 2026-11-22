package com.hrms.payroll.dto.response;

import java.math.BigDecimal;

/**
 * A loan of the register. remainingBalance = principal - instalments paid x instalment (0 once all are paid);
 * lastMonth is the month of the last instalment; finalInstalment is what that one takes after rounding.
 */
public record EmployeeLoanResponse(
        Long       loanId,
        Long       employeeId,
        String     employeeCode,
        String     employeeName,
        Long       componentId,
        String     componentCode,
        String     loanTypeName,
        String     loanTypeNameAr,
        BigDecimal principal,
        Integer    instalmentCount,
        BigDecimal monthlyInstalment,
        BigDecimal finalInstalment,
        Integer    instalmentsPaid,
        BigDecimal remainingBalance,
        String     startMonth,
        String     lastMonth,
        String     status,
        String     statusReason,
        String     remarks,
        Long       version
) {}
