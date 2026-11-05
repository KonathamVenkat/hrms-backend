package com.hrms.auth.security;

import com.hrms.common.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Who decides whose request: HR's requests go to the General Manager, the General Manager's to the CEO. */
class ApprovalRoutingTest {

    @Test
    void anEmployeesRequestIsDecidedByHrAdminOrHrManager() {
        assertEquals(EnumSet.of(UserRole.HR_ADMIN, UserRole.HR_MANAGER), ApprovalRouting.deciders(UserRole.EMPLOYEE));
        assertEquals(EnumSet.of(UserRole.HR_ADMIN, UserRole.HR_MANAGER), ApprovalRouting.deciders(UserRole.CEO));
        assertEquals(EnumSet.of(UserRole.HR_ADMIN, UserRole.HR_MANAGER), ApprovalRouting.deciders(UserRole.SYSTEM_ADMIN));
    }

    @Test
    void aRequestFromHrIsDecidedOnlyByTheGeneralManager() {
        assertEquals(EnumSet.of(UserRole.GENERAL_MANAGER), ApprovalRouting.deciders(UserRole.HR_ADMIN));
        assertEquals(EnumSet.of(UserRole.GENERAL_MANAGER), ApprovalRouting.deciders(UserRole.HR_MANAGER));
    }

    @Test
    void theGeneralManagersRequestIsDecidedOnlyByTheCeo() {
        assertEquals(EnumSet.of(UserRole.CEO), ApprovalRouting.deciders(UserRole.GENERAL_MANAGER));
    }

    @Test
    void aWorkflowRoleStepPassesToTheLevelAboveForARequesterOfThatGroup() {
        assertEquals("CEO", ApprovalRouting.stepRole("GENERAL_MANAGER", UserRole.GENERAL_MANAGER));
        assertEquals("GENERAL_MANAGER", ApprovalRouting.stepRole("HR_ADMIN", UserRole.HR_ADMIN));
        assertEquals("GENERAL_MANAGER", ApprovalRouting.stepRole("HR_ADMIN", UserRole.HR_MANAGER));
        assertEquals("GENERAL_MANAGER", ApprovalRouting.stepRole("HR_MANAGER", UserRole.HR_ADMIN));
    }

    @Test
    void everyoneElseKeepsTheStepsOwnRole() {
        assertEquals("GENERAL_MANAGER", ApprovalRouting.stepRole("GENERAL_MANAGER", UserRole.EMPLOYEE));
        assertEquals("GENERAL_MANAGER", ApprovalRouting.stepRole("GENERAL_MANAGER", UserRole.HR_ADMIN));   // HR's request still reaches the GM step
        assertEquals("HR_ADMIN", ApprovalRouting.stepRole("HR_ADMIN", UserRole.EMPLOYEE));
        assertEquals("HR_ADMIN", ApprovalRouting.stepRole("HR_ADMIN", UserRole.GENERAL_MANAGER));
        assertEquals("GENERAL_MANAGER", ApprovalRouting.stepRole("GENERAL_MANAGER", UserRole.CEO));
        assertEquals("HR_ADMIN", ApprovalRouting.stepRole("HR_ADMIN", UserRole.CEO));
    }
}
