package com.hrms.employee.service;

import com.hrms.employee.dto.request.EducationLevelRequest;
import com.hrms.employee.dto.response.EducationLevelResponse;
import com.hrms.employee.dto.response.MasterLookupResponse;
import java.util.List;

public interface EducationLevelService {
    /** Active rows for dropdowns (read-only, for the roles that pick them). */
    List<MasterLookupResponse> getActiveLookup();
    List<EducationLevelResponse> getAll();
    EducationLevelResponse getById(Long id);
    EducationLevelResponse create(EducationLevelRequest request);
    EducationLevelResponse update(Long id, EducationLevelRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
