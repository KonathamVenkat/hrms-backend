package com.hrms.employee.service;

import com.hrms.employee.dto.request.LanguageLevelRequest;
import com.hrms.employee.dto.response.LanguageLevelResponse;
import java.util.List;

public interface LanguageLevelService {
    List<LanguageLevelResponse> getAll();
    LanguageLevelResponse getById(Long id);
    LanguageLevelResponse create(LanguageLevelRequest request);
    LanguageLevelResponse update(Long id, LanguageLevelRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
