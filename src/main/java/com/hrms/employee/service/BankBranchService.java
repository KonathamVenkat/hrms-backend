package com.hrms.employee.service;

import com.hrms.employee.dto.request.BankBranchRequest;
import com.hrms.employee.dto.response.BankBranchResponse;
import com.hrms.employee.dto.response.MasterLookupResponse;
import java.util.List;

public interface BankBranchService {
    /** Active rows of one parent for dropdowns. */
    List<MasterLookupResponse> getActiveLookupByBankId(Long bankId);
    List<BankBranchResponse> getAll();
    BankBranchResponse getById(Long id);
    BankBranchResponse create(BankBranchRequest request);
    BankBranchResponse update(Long id, BankBranchRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
