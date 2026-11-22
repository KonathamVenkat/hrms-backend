package com.hrms.payroll.repository.projection;

import java.math.BigDecimal;

/** One loan of the register with the names to show (see EmployeeLoanRepository.search). */
public interface EmployeeLoanRow {
    Long       getLoanId();
    Long       getEmployeeId();
    String     getEmployeeCode();
    String     getEmployeeName();
    Long       getComponentId();
    String     getComponentCode();
    String     getLoanTypeName();
    String     getLoanTypeNameAr();
    BigDecimal getPrincipal();
    Integer    getInstalmentCount();
    BigDecimal getMonthlyInstalment();
    String     getStartMonth();
    Integer    getInstalmentsPaid();
    String     getStatus();
    String     getStatusReason();
    String     getRemarks();
    Long       getVersion();
}
