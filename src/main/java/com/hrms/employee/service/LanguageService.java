package com.hrms.employee.service;

import com.hrms.employee.dto.request.LanguageRequest;
import com.hrms.employee.dto.response.LanguageResponse;
import com.hrms.employee.dto.response.MasterLookupResponse;
import java.util.List;

public interface LanguageService {
    /** Active rows for dropdowns (read-only, for the roles that pick them). */
    List<MasterLookupResponse> getActiveLookup();
    List<LanguageResponse> getAll();
    LanguageResponse getById(Long id);
    LanguageResponse create(LanguageRequest request);
    LanguageResponse update(Long id, LanguageRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
