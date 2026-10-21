package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.employee.repository.CityRepository;
import com.hrms.employee.repository.ProvinceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The city page request is bounded before it reaches the database. */
class CityServiceSearchTest {

    private final CityRepository repository = mock(CityRepository.class);
    private final CityServiceImpl service = new CityServiceImpl(
        repository, mock(ProvinceRepository.class), mock(AuditTrail.class), mock(EntityManager.class));

    @Test
    void refusesANegativePageOrAnOversizedPage() {
        assertThrows(BadRequestException.class, () -> service.search(null, null, null, -1, 20));
        assertThrows(BadRequestException.class, () -> service.search(null, null, null, 0, 0));
        assertThrows(BadRequestException.class, () -> service.search(null, null, null, 0, 101));
        verifyNoInteractions(repository);
    }

    @Test
    void refusesSearchTextLongerThan100Characters() {
        assertThrows(BadRequestException.class, () -> service.search("x".repeat(101), null, null, 0, 20));
        verifyNoInteractions(repository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsTheRequestedPageOrderedByName() {
        Page<com.hrms.employee.entity.City> empty = new PageImpl<>(List.of(), PageRequest.of(2, 50), 0);
        when(repository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class))).thenReturn(empty);
        var result = service.search("juba", 5L, true, 2, 50);
        assertEquals(0, result.getTotalElements());
        verify(repository).findAll(any(Specification.class),
            org.mockito.ArgumentMatchers.eq(PageRequest.of(2, 50, org.springframework.data.domain.Sort.by("name", "id"))));
    }
}
