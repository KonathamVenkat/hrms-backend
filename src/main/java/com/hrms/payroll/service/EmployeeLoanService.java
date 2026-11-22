package com.hrms.payroll.service;

import com.hrms.common.dto.PagedResponse;
import com.hrms.payroll.dto.request.EmployeeLoanRequest;
import com.hrms.payroll.dto.request.LoanStatusRequest;
import com.hrms.payroll.dto.response.EmployeeLoanResponse;
import com.hrms.payroll.dto.response.LoanTypeResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The loan register (payroll step 4b-1): HR_ADMIN records approved loans; payroll runs (step 5) repay them. */
public interface EmployeeLoanService {

    PagedResponse<EmployeeLoanResponse> search(String q, Long componentId, String status, Long employeeId, Pageable pageable);

    List<LoanTypeResponse> loanTypes();

    EmployeeLoanResponse create(EmployeeLoanRequest request);

    /** Repaid early: the remaining balance is settled outside payroll. */
    EmployeeLoanResponse close(Long loanId, LoanStatusRequest request);

    /** Entered by mistake. */
    EmployeeLoanResponse cancel(Long loanId, LoanStatusRequest request);

    /** What the employee's running loans take in the month (0 when none). */
    BigDecimal monthlyInstalments(Long employeeId, LocalDate month);
}
