package com.hrms.attendance.enums;

/**
 * Type of overtime request.
 *
 * PRE_APPROVED  → Employee notifies HR before working overtime
 * POST_FACTO    → Employee logs overtime after completing the work
 * WEEKEND       → Work done on Friday/Saturday (Oman weekend)
 * HOLIDAY       → Work done on a public holiday
 */
public enum OvertimeType {
    PRE_APPROVED,
    POST_FACTO,
    WEEKEND,
    HOLIDAY
}
