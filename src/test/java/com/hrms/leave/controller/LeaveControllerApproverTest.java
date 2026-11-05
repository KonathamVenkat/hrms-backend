package com.hrms.leave.controller;

import com.hrms.leave.dto.request.ApproveLeaveRequest;
import com.hrms.leave.service.LeaveService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The security context holds the login name as a plain string, so the approver must be read from the Authentication.
 * (Reading it as a UserDetails gave null and every approval was recorded as approved by "SYSTEM".)
 */
class LeaveControllerApproverTest {

    @Test
    void theApproverPassedToTheServiceIsTheSignedInName() {
        LeaveService service = mock(LeaveService.class);
        LeaveController controller = new LeaveController(service);
        var auth = new UsernamePasswordAuthenticationToken("hr.manager", null, List.of());
        ApproveLeaveRequest request = new ApproveLeaveRequest("APPROVED", null);

        controller.processLeave(41L, request, auth);

        verify(service).processLeave(eq(41L), any(), eq("hr.manager"));
    }
}
