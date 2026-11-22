package com.hrms.employee.service;

import com.hrms.employee.dto.request.GradeRequest;
import com.hrms.employee.dto.response.GradeResponse;
import com.hrms.employee.dto.response.GradeScaleStepResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GradeService {
    List<GradeResponse> getAll();
    GradeResponse getById(Long id);
    /** The pay scale in effect today, active grades only, for the job-details grade and increment dropdowns. */
    List<GradeScaleStepResponse> getPayScale();
    /** The starting scale of a grade and increment in effect on a date; empty when the scale has none then. */
    Optional<BigDecimal> findStartingScale(Long gradeId, Integer incrementNo, LocalDate onDate);
    /** The grade's English and Arabic name, for display; empty when the grade does not exist. */
    Optional<GradeResponse> findGrade(Long gradeId);
    GradeResponse create(GradeRequest request);
    GradeResponse update(Long id, GradeRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while nothing refers to the row. */
    void delete(Long id);
}
