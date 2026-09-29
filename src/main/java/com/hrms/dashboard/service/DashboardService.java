package com.hrms.dashboard.service;

import com.hrms.dashboard.dto.response.HrDashboardResponse;

public interface DashboardService {

    /** Headline workforce and pending-approval numbers for HR_ADMIN / HR_MANAGER. */
    HrDashboardResponse getHrSummary();
}
