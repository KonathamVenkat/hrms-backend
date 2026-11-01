package com.hrms.employee.service;

import com.hrms.employee.dto.request.SectionRequest;
import com.hrms.employee.dto.response.SectionResponse;
import com.hrms.employee.dto.response.SectionLookupResponse;
import java.util.List;

public interface SectionService {
    List<SectionResponse> getAll();
    /** Active sections of one department, for the cascading dropdown. */
    List<SectionLookupResponse> getActiveLookupByDepartment(Long departmentId);
    SectionResponse getById(Long id);
    SectionResponse create(SectionRequest request);
    SectionResponse update(Long id, SectionRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
