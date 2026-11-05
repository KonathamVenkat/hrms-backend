package com.hrms.auth.security;

import com.hrms.common.enums.UserRole;

import java.util.EnumSet;
import java.util.Set;

/**
 * Who decides whose request. One definition for the approval workflow and for the older approve/reject screens
 * (leave, overtime, attendance regularization), so the two cannot drift apart.
 *
 * <ul>
 *   <li>A request from an HR_ADMIN or HR_MANAGER is decided by the GENERAL_MANAGER.</li>
 *   <li>A request from the GENERAL_MANAGER is decided by the CEO.</li>
 *   <li>Everyone else's request (employees, the CEO) is decided by HR_ADMIN or HR_MANAGER.</li>
 * </ul>
 * Nobody ever decides their own request; that is checked separately where the request is decided.
 */
public final class ApprovalRouting {

    private ApprovalRouting() {}

    /** The roles that may decide a request made by an employee whose login has {@code requester}. */
    public static Set<UserRole> deciders(UserRole requester) {
        return switch (requester) {
            case GENERAL_MANAGER -> EnumSet.of(UserRole.CEO);
            case HR_ADMIN, HR_MANAGER -> EnumSet.of(UserRole.GENERAL_MANAGER);
            default -> EnumSet.of(UserRole.HR_ADMIN, UserRole.HR_MANAGER);
        };
    }

    /**
     * The role that actually holds a workflow ROLE step for this requester. The step names the usual role; when the
     * requester belongs to that group the step passes to the next level up instead: the General Manager's own request
     * reaches the CEO at the General Manager step, and an HR_ADMIN's or HR_MANAGER's request reaches the General
     * Manager at an HR step.
     */
    public static String stepRole(String stepRole, UserRole requester) {
        if (stepRole == null || requester == null) {
            return stepRole;
        }
        if (requester == UserRole.GENERAL_MANAGER && UserRole.GENERAL_MANAGER.name().equals(stepRole)) {
            return UserRole.CEO.name();
        }
        boolean hrStep = UserRole.HR_ADMIN.name().equals(stepRole) || UserRole.HR_MANAGER.name().equals(stepRole);
        if (hrStep && (requester == UserRole.HR_ADMIN || requester == UserRole.HR_MANAGER)) {
            return UserRole.GENERAL_MANAGER.name();
        }
        return stepRole;
    }

    /** The message for a caller who is not one of {@link #deciders}. */
    public static String refusal(UserRole requester) {
        return switch (requester) {
            case GENERAL_MANAGER -> "A request from the General Manager can only be decided by the CEO.";
            case HR_ADMIN, HR_MANAGER ->
                "A request from an HR administrator or HR manager can only be decided by the General Manager.";
            default -> "Only an HR administrator or HR manager can decide this request.";
        };
    }
}
