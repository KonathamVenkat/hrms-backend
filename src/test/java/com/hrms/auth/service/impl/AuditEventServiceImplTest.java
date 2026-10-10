package com.hrms.auth.service.impl;

import com.hrms.auth.dto.response.AuditEventResponse;
import com.hrms.auth.entity.AuditEvent;
import com.hrms.auth.repository.AuditEventRepository;
import com.hrms.common.dto.PagedResponse;
import com.hrms.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The audit read side: newest first, page size capped, bad date ranges refused, fields passed through. */
class AuditEventServiceImplTest {

    AuditEventRepository repo = mock(AuditEventRepository.class);
    AuditEventServiceImpl service = new AuditEventServiceImpl(repo);

    @SuppressWarnings("unchecked")
    private Pageable searched(int page, int size) {
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        service.search(null, null, null, null, null, null, page, size);
        ArgumentCaptor<Pageable> captured = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findAll(any(Specification.class), captured.capture());
        return captured.getValue();
    }

    @Test
    void newestEventsComeFirst() {
        Sort sort = searched(0, 20).getSort();

        assertEquals(Sort.Direction.DESC, sort.getOrderFor("eventTime").getDirection());
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("id").getDirection());
    }

    @Test
    void aHugePageSizeIsCapped() {
        assertEquals(100, searched(0, 100000).getPageSize());
    }

    @Test
    void aNegativePageAndZeroSizeAreCorrected() {
        Pageable p = searched(-3, 0);

        assertEquals(0, p.getPageNumber());
        assertEquals(1, p.getPageSize());
    }

    @Test
    void anEndDateBeforeTheStartDateIsRefused() {
        var ex = assertThrows(BusinessRuleException.class, () -> service.search(
                null, null, null, null, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 1), 0, 20));

        assertEquals("INVALID_DATES", ex.getRuleCode());
        verifyNoInteractions(repo);
    }

    @Test
    @SuppressWarnings("unchecked")
    void eventFieldsAreReturnedAsStored() {
        LocalDateTime when = LocalDateTime.of(2026, 10, 9, 8, 30);
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                AuditEvent.builder().id(7L).eventTime(when).actor("admin").action("PASSWORD_RESET")
                        .targetType("USER").targetId("12").detail("target sara").build())));

        PagedResponse<AuditEventResponse> r = service.search(null, null, null, null, null, null, 0, 20);

        assertEquals(1, r.getTotalElements());
        assertEquals(new AuditEventResponse(7L, when, "admin", "PASSWORD_RESET", "USER", "12", "target sara"),
                r.getContent().get(0));
    }
}
