package com.hrms.recruitment.workflow;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.recruitment.enums.RequisitionStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.hrms.recruitment.enums.RequisitionStatus.*;

/**
 * The one table of allowed requisition moves. Each service action checks it before it changes anything,
 * so a second approve, a cancel after rejection or a reopened rejection is refused (HTTP 409).
 * FILLED is reached by the hiring flow, not by an action of its own.
 */
@Component
public class RequisitionWorkflow {

    private static final Map<RequisitionStatus, Set<RequisitionStatus>> ALLOWED = new EnumMap<>(RequisitionStatus.class);

    static {
        ALLOWED.put(DRAFT, EnumSet.of(PENDING_APPROVAL, CANCELLED));
        ALLOWED.put(PENDING_APPROVAL, EnumSet.of(APPROVED, REJECTED, CANCELLED));
        ALLOWED.put(APPROVED, EnumSet.of(ON_HOLD, FILLED, CLOSED, CANCELLED));
        ALLOWED.put(ON_HOLD, EnumSet.of(APPROVED, CLOSED, CANCELLED));
    }

    public boolean canMove(RequisitionStatus from, RequisitionStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** @throws BusinessRuleException REQUISITION_INVALID_STATE when the move is not allowed */
    public void assertCanMove(RequisitionStatus from, RequisitionStatus to) {
        if (!canMove(from, to)) {
            throw new BusinessRuleException("REQUISITION_INVALID_STATE",
                "A requisition that is " + from + " cannot become " + to + ".");
        }
    }
}
