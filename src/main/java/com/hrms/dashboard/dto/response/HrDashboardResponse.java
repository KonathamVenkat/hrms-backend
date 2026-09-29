package com.hrms.dashboard.dto.response;

/**
 * Headline numbers for the HR/Manager dashboard. All counts are computed server-side
 * for "today" and the current Sunday–Saturday week.
 */
public record HrDashboardResponse(
        long totalStrength,            // active employees currently employed (not exited)
        long newJoinersToday,          // hire date = today
        long joiningThisWeek,          // hire date within the current Sunday–Saturday week
        long onLeaveToday,             // distinct employees with an APPROVED leave covering today
        long pendingLeaveRequests,
        long pendingRegularizations,
        long pendingOvertimeRequests
) {}
