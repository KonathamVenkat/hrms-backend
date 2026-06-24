package com.hrms.attendance.enums;

/**
 * Status lifecycle for an attendance regularization request.
 *
 * PENDING   → initial state when employee submits
 * APPROVED  → manager/HR approved; attendance log is corrected
 * REJECTED  → manager/HR rejected with reason
 * CANCELLED → employee cancelled before decision
 */
public enum RegularizationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
