package com.hrms.employee.service;

import com.hrms.employee.dto.request.EmployeeBankAccountRequest;
import com.hrms.employee.dto.response.EmployeeBankAccountResponse;

import java.util.List;

public interface EmployeeBankAccountService {
    /** Active accounts first, primary first. Numbers are masked unless the caller is HR_ADMIN or the employee. */
    List<EmployeeBankAccountResponse> getAccounts(Long employeeId);
    EmployeeBankAccountResponse addAccount(Long employeeId, EmployeeBankAccountRequest request);
    EmployeeBankAccountResponse updateAccount(Long employeeId, Long accountId, EmployeeBankAccountRequest request);
    void setPrimary(Long employeeId, Long accountId);
    /** An account is never deleted. The primary one can only be deactivated when it is the last active account. */
    void deactivateAccount(Long employeeId, Long accountId);
}
