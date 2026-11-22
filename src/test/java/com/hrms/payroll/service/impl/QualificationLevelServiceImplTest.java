package com.hrms.payroll.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.payroll.dto.request.QualificationLevelRequest;
import com.hrms.payroll.entity.QualificationLevel;
import com.hrms.payroll.repository.QualificationLevelRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The master's own rules: code and name unique (code upper-cased), a version to update, every change audited. */
class QualificationLevelServiceImplTest {

    QualificationLevelRepository repository = mock(QualificationLevelRepository.class);
    AuditTrail audit = mock(AuditTrail.class);
    QualificationLevelServiceImpl service = new QualificationLevelServiceImpl(repository, audit, mock(EntityManager.class));

    private QualificationLevelRequest request(Long version) {
        return new QualificationLevelRequest(" ql_bachelor ", " Bachelor ", " بكالوريوس ", new BigDecimal("202500"), 3, null, version);
    }

    @Test
    void createTrimsUpperCasesTheCodeAndAudits() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        var created = service.create(request(null));
        assertEquals("QL_BACHELOR", created.code());
        assertEquals("Bachelor", created.name());
        assertEquals(true, created.isActive());
        verify(audit).record(eq("QUALIFICATION_LEVEL_CREATED"), eq("QUALIFICATION_LEVEL"), any(), any());
    }

    @Test
    void aDuplicateCodeOrNameIsRefused() {
        when(repository.existsByCodeIgnoreCase("QL_BACHELOR")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.create(request(null)));
        when(repository.existsByCodeIgnoreCase("QL_BACHELOR")).thenReturn(false);
        when(repository.existsByNameIgnoreCase("Bachelor")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.create(request(null)));
        verify(repository, never()).save(any());
    }

    @Test
    void anUpdateNeedsTheVersionTheScreenWasOpenedWith() {
        when(repository.findById(1L)).thenReturn(Optional.of(QualificationLevel.builder().id(1L).version(4L).isActive(true).build()));
        assertThrows(BadRequestException.class, () -> service.update(1L, request(null)));
        assertThrows(RuntimeException.class, () -> service.update(1L, request(3L)));
        verify(repository, never()).save(any());
    }
}
