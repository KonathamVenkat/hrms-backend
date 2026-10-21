package com.hrms.recruitment;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.recruitment.enums.RequisitionStatus;
import com.hrms.recruitment.workflow.RequisitionWorkflow;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.hrms.recruitment.enums.RequisitionStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class RequisitionWorkflowTest {

    private final RequisitionWorkflow workflow = new RequisitionWorkflow();

    private static final Set<String> ALLOWED = Set.of(
        "DRAFT>PENDING_APPROVAL", "DRAFT>CANCELLED",
        "PENDING_APPROVAL>APPROVED", "PENDING_APPROVAL>REJECTED", "PENDING_APPROVAL>CANCELLED",
        "APPROVED>ON_HOLD", "APPROVED>FILLED", "APPROVED>CLOSED", "APPROVED>CANCELLED",
        "ON_HOLD>APPROVED", "ON_HOLD>CLOSED", "ON_HOLD>CANCELLED");

    @Test
    void exactlyTheDocumentedMovesAreAllowed() {
        for (RequisitionStatus from : values()) {
            for (RequisitionStatus to : values()) {
                assertEquals(ALLOWED.contains(from + ">" + to), workflow.canMove(from, to), from + " -> " + to);
            }
        }
    }

    @Test
    void finishedRequisitionsCannotMoveAnywhere() {
        for (RequisitionStatus finished : new RequisitionStatus[] {REJECTED, FILLED, CLOSED, CANCELLED}) {
            for (RequisitionStatus to : values()) {
                assertFalse(workflow.canMove(finished, to), finished + " -> " + to);
            }
        }
    }

    @Test
    void anIllegalMoveIsARuleViolationWithAStableCode() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> workflow.assertCanMove(APPROVED, APPROVED));
        assertEquals("REQUISITION_INVALID_STATE", ex.getRuleCode());
    }
}
