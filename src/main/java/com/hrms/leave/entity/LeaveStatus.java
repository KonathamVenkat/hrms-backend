package com.hrms.leave.entity;

/**
 * Status lifecycle for a leave request:
 *
 *  PENDING   → APPROVED  (manager/HR approves)
 *  PENDING   → REJECTED  (manager/HR rejects)
 *  PENDING   → CANCELLED (employee cancels before decision)
 */
public enum LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}