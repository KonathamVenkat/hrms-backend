package com.hrms.workflow.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.dto.PagedResponse;
import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.employee.DatabaseTest;
import com.hrms.employee.EmployeeApplication;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.leave.entity.LeaveRequest;
import com.hrms.leave.repository.LeaveRequestRepository;
import com.hrms.workflow.dto.response.InboxItemResponse;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.service.InboxService;
import com.hrms.workflow.service.WorkflowDefinitionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * The approvals inbox on real Oracle: only rows whose stored current approver is the caller (person) or one of the
 * caller's roles, never the caller's own request, oldest first, the page size capped.
 */
@SpringBootTest(classes = EmployeeApplication.class)
@Transactional
class InboxServiceDbTest extends DatabaseTest {

    @Autowired InboxService inbox;
    @Autowired LeaveRequestRepository requests;
    @Autowired EmployeeRepository employees;
    @Autowired WorkflowDefinitionService definitions;
    @MockitoBean EmployeeAccessGuard guard;

    private long me;
    private long other;
    private long requester;
    private long definitionId;
    private Long caller;

    @BeforeEach
    void setUp() {
        String run = Long.toString(System.nanoTime(), 36);
        me = employee("me", run);
        other = employee("other", run);
        requester = employee("req", run);
        definitionId = definitions.publishDefault(RequestType.LEAVE).getDefinitionId();
        when(guard.currentEmployeeId()).thenAnswer(i -> caller);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private long employee(String tag, String run) {
        return employees.saveAndFlush(Employee.builder()
            .employeeCode("IB-" + tag + "-" + run).firstName("Ib").firstNameAr("ع").lastName(tag).lastNameAr("ل")
            .dateOfBirth(LocalDate.of(1990, 1, 1)).gender(Gender.MALE)
            .personalEmail("ib-" + tag + "-" + run + "-p@example.test")
            .workEmail("ib-" + tag + "-" + run + "-w@example.test")
            .hireDate(LocalDate.of(2024, 1, 1)).employmentType(EmploymentType.FULL_TIME).build()).getId();
    }

    private void as(Long employeeId, String role) {
        caller = employeeId;
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "user", "n/a", AuthorityUtils.createAuthorityList("ROLE_" + role)));
    }

    /** A request waiting at {@code step}; exactly one of approver / role is normally set. */
    private long waiting(long owner, String step, Long approver, String role, LocalDateTime submitted) {
        LeaveRequest lr = LeaveRequest.builder().employeeId(owner).employeeCode("IB-" + owner).leaveTypeCode("ANNUAL")
            .startDate(LocalDate.of(2027, 3, 1)).endDate(LocalDate.of(2027, 3, 3)).totalDays(3.0)
            .workflowDefinitionId(definitionId).stage(step == null ? "APPROVED" : "PENDING_" + step)
            .currentStepKey(step).currentApproverEmployeeId(approver).currentApproverRole(role)
            .createdAt(submitted).build();
        return requests.saveAndFlush(lr).getLeaveReqId();
    }

    private List<Long> ids(PagedResponse<InboxItemResponse> page) {
        return page.getContent().stream().map(InboxItemResponse::requestId).toList();
    }

    @Test
    void personStep_showsOnlyToTheNamedApprover() {
        long mine = waiting(requester, "BRANCH", me, null, LocalDateTime.now());
        long theirs = waiting(requester, "BRANCH", other, null, LocalDateTime.now());

        as(me, "EMPLOYEE");
        List<Long> seen = ids(inbox.inbox(0, 100));
        assertTrue(seen.contains(mine));
        assertFalse(seen.contains(theirs));

        as(other, "EMPLOYEE");
        List<Long> otherSeen = ids(inbox.inbox(0, 100));
        assertTrue(otherSeen.contains(theirs));
        assertFalse(otherSeen.contains(mine));
    }

    @Test
    void roleStep_showsToEveryHolderOfThatRoleOnly() {
        long hr = waiting(requester, "HR", null, "HR_ADMIN", LocalDateTime.now());

        as(me, "HR_ADMIN");
        assertTrue(ids(inbox.inbox(0, 100)).contains(hr));
        as(other, "HR_MANAGER");
        assertFalse(ids(inbox.inbox(0, 100)).contains(hr));
        as(other, "EMPLOYEE");
        assertFalse(ids(inbox.inbox(0, 100)).contains(hr));
    }

    @Test
    void ownRequestNeverShowsEvenWhenTheRoleMatches() {
        long own = waiting(me, "HR", null, "HR_ADMIN", LocalDateTime.now());

        as(me, "HR_ADMIN");
        assertFalse(ids(inbox.inbox(0, 100)).contains(own));
    }

    @Test
    void closedRequestsAndLegacyRowsNeverShow() {
        long closed = waiting(requester, null, null, null, LocalDateTime.now());

        as(me, "HR_ADMIN");
        assertFalse(ids(inbox.inbox(0, 100)).contains(closed));
    }

    @Test
    void callerWithNothingWaitingGetsAnEmptyPage() {
        as(null, "NO_SUCH_ROLE");
        PagedResponse<InboxItemResponse> page = inbox.inbox(0, 10);

        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
    }

    @Test
    void oldestFirst_withLabelsNameAndSummary() {
        LocalDateTime now = LocalDateTime.now();
        long newer = waiting(requester, "SECRETARY", me, null, now);
        long older = waiting(requester, "SECRETARY", me, null, now.minusDays(2));

        as(me, "EMPLOYEE");
        List<InboxItemResponse> items = inbox.inbox(0, 100).getContent();
        List<Long> seen = items.stream().map(InboxItemResponse::requestId).toList();
        assertTrue(seen.indexOf(older) >= 0 && seen.indexOf(older) < seen.indexOf(newer));

        InboxItemResponse item = items.get(seen.indexOf(newer));
        assertEquals(RequestType.LEAVE, item.type());
        assertEquals("SECRETARY", item.stepKey());
        assertEquals(requester, item.requesterEmployeeId());
        assertEquals("Ib req", item.requesterName());
        assertTrue(item.stepLabelEn() != null && !item.stepLabelEn().isBlank());
        assertTrue(item.stepLabelAr() != null && !item.stepLabelAr().isBlank());
        assertTrue(item.summary().contains("ANNUAL") && item.summary().contains("2027-03-01"));
    }

    @Test
    void pageSizeIsCappedAndNegativePageIsZero() {
        as(me, "HR_ADMIN");

        assertEquals(100, inbox.inbox(0, 10_000).getSize());
        assertEquals(1, inbox.inbox(0, 0).getSize());
        assertEquals(0, inbox.inbox(-5, 10).getNumber());
    }
}
