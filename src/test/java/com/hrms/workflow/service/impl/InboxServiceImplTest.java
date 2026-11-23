package com.hrms.workflow.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.dto.PagedResponse;
import com.hrms.workflow.dto.response.InboxItemResponse;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.repository.WorkflowStepRepository;
import com.hrms.workflow.service.InboxSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The inbox merges every request type's waiting rows, oldest first, into one correctly paged list. */
class InboxServiceImplTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 23, 9, 0);

    EmployeeAccessGuard guard = mock(EmployeeAccessGuard.class);
    WorkflowStepRepository steps = mock(WorkflowStepRepository.class);

    /** A source over a fixed list, paging and sorting like the real ones (oldest first, then id). */
    record FakeSource(RequestType type, List<Row> rows, List<Pageable> calls) implements InboxSource {
        FakeSource(RequestType type, Row... rows) {
            this(type, List.of(rows), new ArrayList<>());
        }

        @Override
        public Page<Row> waitingFor(long employeeId, Collection<String> roles, Pageable pageable) {
            calls.add(pageable);
            List<Row> sorted = rows.stream()
                .sorted(Comparator.comparing(Row::submittedAt).thenComparingLong(Row::requestId)).toList();
            int from = (int) Math.min(pageable.getOffset(), sorted.size());
            int to = Math.min(from + pageable.getPageSize(), sorted.size());
            return new PageImpl<>(sorted.subList(from, to), pageable, sorted.size());
        }
    }

    private static InboxSource.Row row(long id, int minutes) {
        return new InboxSource.Row(id, null, "HEAD", 40 + id, "Person " + id, "line " + id, T0.plusMinutes(minutes),
            Map.of("n", id));
    }

    @BeforeEach
    void signedIn() {
        when(guard.currentEmployeeId()).thenReturn(7L);
        when(steps.findByDefinitionIdOrderByPositionNo(anyLong())).thenReturn(List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "me", null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));
    }

    @AfterEach
    void signedOut() {
        SecurityContextHolder.clearContext();
    }

    private static List<String> ids(PagedResponse<InboxItemResponse> page) {
        return page.getContent().stream().map(i -> i.type().name().charAt(0) + "" + i.requestId()).toList();
    }

    @Test
    void oneSourceIsPagedByItsOwnQuery() {
        FakeSource leave = new FakeSource(RequestType.LEAVE, row(1, 5), row(2, 1), row(3, 9));
        InboxServiceImpl inbox = new InboxServiceImpl(List.of(leave), guard, steps);

        PagedResponse<InboxItemResponse> page = inbox.inbox(1, 2);

        assertEquals(List.of("L3"), ids(page));
        assertEquals(3, page.getTotalElements());
        assertEquals(1, leave.calls().getFirst().getPageNumber());   // the source's own page, not a merge read
        assertEquals("line 3", page.getContent().getFirst().summary());
        assertEquals(Map.of("n", 3L), page.getContent().getFirst().summaryDetails());
    }

    @Test
    void twoSourcesMergeOldestFirstAcrossPages() {
        FakeSource leave = new FakeSource(RequestType.LEAVE, row(1, 0), row(2, 20), row(3, 40));
        FakeSource overtime = new FakeSource(RequestType.OVERTIME, row(10, 10), row(11, 30), row(12, 50), row(13, 60));
        InboxServiceImpl inbox = new InboxServiceImpl(List.of(leave, overtime), guard, steps);

        PagedResponse<InboxItemResponse> first = inbox.inbox(0, 3);
        PagedResponse<InboxItemResponse> second = inbox.inbox(1, 3);
        PagedResponse<InboxItemResponse> third = inbox.inbox(2, 3);

        assertEquals(List.of("L1", "O10", "L2"), ids(first));
        assertEquals(List.of("O11", "L3", "O12"), ids(second));
        assertEquals(List.of("O13"), ids(third));
        assertEquals(7, first.getTotalElements());
        assertEquals(3, first.getTotalPages());
        assertEquals(RequestType.OVERTIME, first.getContent().get(1).type());
    }

    @Test
    void sameTimeIsOrderedByTypeThenIdSoPagesNeverRepeatOrSkip() {
        FakeSource leave = new FakeSource(RequestType.LEAVE, row(5, 0), row(4, 0));
        FakeSource overtime = new FakeSource(RequestType.OVERTIME, row(1, 0));
        InboxServiceImpl inbox = new InboxServiceImpl(List.of(overtime, leave), guard, steps);

        assertEquals(List.of("L4", "L5"), ids(inbox.inbox(0, 2)));
        assertEquals(List.of("O1"), ids(inbox.inbox(1, 2)));
    }

    @Test
    void aPageBeyondTheEndIsEmptyWithTheRightTotal() {
        FakeSource leave = new FakeSource(RequestType.LEAVE, row(1, 0));
        FakeSource overtime = new FakeSource(RequestType.OVERTIME, row(2, 1));
        PagedResponse<InboxItemResponse> page = new InboxServiceImpl(List.of(leave, overtime), guard, steps).inbox(5, 10);

        assertEquals(List.of(), ids(page));
        assertEquals(2, page.getTotalElements());
    }

    @Test
    void noSourceOrTwoSourcesForOneTypeFailAtStartup() {
        assertThrows(IllegalStateException.class, () -> new InboxServiceImpl(List.of(), guard, steps));
        assertThrows(IllegalStateException.class, () -> new InboxServiceImpl(
            List.of(new FakeSource(RequestType.LEAVE), new FakeSource(RequestType.LEAVE)), guard, steps));
    }
}
