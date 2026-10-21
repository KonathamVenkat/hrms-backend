package com.hrms.recruitment.enums;

/** Lifecycle of a job requisition; the allowed moves are in {@code RequisitionWorkflow}. */
public enum RequisitionStatus {
    DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, ON_HOLD, FILLED, CLOSED, CANCELLED
}
