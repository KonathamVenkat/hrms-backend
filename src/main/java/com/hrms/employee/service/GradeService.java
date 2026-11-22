package com.hrms.employee.service;

import com.hrms.employee.dto.request.GradeRequest;
import com.hrms.employee.dto.response.GradeResponse;
import com.hrms.employee.dto.response.GradeScaleStepResponse;
import java.util.List;

public interface GradeService {
    List<GradeResponse> getAll();
    GradeResponse getById(Long id);
    /** The pay scale in effect today, active grades only, for the job-details grade and increment dropdowns. */
    List<GradeScaleStepResponse> getPayScale();
    GradeResponse create(GradeRequest request);
    GradeResponse update(Long id, GradeRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
