package com.hrms.payroll.service;

import com.hrms.payroll.dto.request.QualificationLevelRequest;
import com.hrms.payroll.dto.response.QualificationLevelResponse;

import java.util.List;

public interface QualificationLevelService {
    List<QualificationLevelResponse> getAll();
    /** Active levels only, for the Assign Salary dropdown. */
    List<QualificationLevelResponse> getActive();
    QualificationLevelResponse getById(Long id);
    QualificationLevelResponse create(QualificationLevelRequest request);
    QualificationLevelResponse update(Long id, QualificationLevelRequest request);
    void deactivate(Long id);
    void activate(Long id);
    /** Hard delete; only allowed while no salary refers to the level. */
    void delete(Long id);
}
