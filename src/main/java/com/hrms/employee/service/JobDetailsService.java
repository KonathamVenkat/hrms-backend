package com.hrms.employee.service;

import com.hrms.employee.dto.request.JobDetailsRequest;
import com.hrms.employee.dto.response.JobDetailsResponse;
import java.util.List;

public interface JobDetailsService {

    /** Current active job record for an employee */
    JobDetailsResponse getCurrentJob(Long employeeId);

    /** Full SCD Type 2 history — newest first */
    List<JobDetailsResponse> getJobHistory(Long employeeId);

    /**
     * Creates new job assignment.
     * Automatically closes the previous current record (SCD Type 2).
     */
    JobDetailsResponse assignJob(Long employeeId, JobDetailsRequest request);

    /**
     * Updates ONLY the current job record's non-structural fields
     * (remarks, shift, work mode) without creating a new history entry.
     */
    JobDetailsResponse updateCurrentJob(Long employeeId, JobDetailsRequest request);
}
