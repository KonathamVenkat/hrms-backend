package com.hrms.employee.service;

import com.hrms.employee.dto.request.BankRequest;
import com.hrms.employee.dto.response.BankResponse;
import com.hrms.employee.dto.response.MasterLookupResponse;
import java.util.List;

public interface BankService {
    /** Active rows for dropdowns (read-only, for the roles that pick them). */
    List<MasterLookupResponse> getActiveLookup();
    List<BankResponse> getAll();
    BankResponse getById(Long id);
    BankResponse create(BankRequest request);
    BankResponse update(Long id, BankRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
