package com.hrms.workflow.service.impl;

import com.hrms.employee.service.OrganisationChanged;
import com.hrms.employee.service.OrganisationLookup;
import com.hrms.workflow.entity.WorkflowStep;
import com.hrms.workflow.enums.ApproverType;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.repository.WorkflowStepRepository;
import com.hrms.workflow.service.WorkflowRequestAccess;
import com.hrms.workflow.service.WorkflowRequestAccess.OpenRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Once per committed transaction, chunked, and a conflicting row never stops the others or reaches the caller. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApproverRefresherTest {

    static final long DEF = 9L;
    static final long DEPT = 7L;

    @Mock WorkflowPorts ports;
    @Mock WorkflowStepRepository steps;
    @Mock OrganisationLookup organisation;
    @Mock PlatformTransactionManager transactions;
    @Mock WorkflowRequestAccess access;
    ApproverRefresher refresher;

    @BeforeEach
    void setUp() {
        refresher = new ApproverRefresher(ports, steps, new ApproverResolver(organisation), transactions);
        WorkflowStep head = ApproverResolverTest.step(ApproverType.DEPARTMENT_HEAD, null);
        head.setStepKey("HEAD");
        when(steps.findPersonStepKeys()).thenReturn(List.of("BRANCH", "HEAD"));
        when(steps.findByDefinitionIdOrderByPositionNo(DEF)).thenReturn(List.of(head));
        when(ports.allAccess()).thenReturn(List.of(access));
        when(access.type()).thenReturn(RequestType.LEAVE);
        when(access.stage(anyLong())).thenReturn(Optional.of("PENDING_HEAD"));
        when(organisation.headOf(DEPT)).thenReturn(Optional.of(50L)); // the new head
    }

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static OpenRequest atHead(long id, Long storedHead) {
        return new OpenRequest(id, 1L, DEPT, DEF, "HEAD", "PENDING_HEAD", storedHead, null);
    }

    @Test
    void twoEventsInOneTransactionRefreshOnceAndOnlyAfterTheCommit() {
        when(access.openRequests(any(), anyLong(), anyInt())).thenReturn(List.of(atHead(1, 40L)));
        TransactionSynchronizationManager.initSynchronization();

        refresher.onOrganisationChanged(new OrganisationChanged());
        refresher.onOrganisationChanged(new OrganisationChanged());

        verifyNoInteractions(access);
        List<TransactionSynchronization> queued = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, queued.size());
        queued.forEach(TransactionSynchronization::afterCommit);
        verify(access, times(1)).openRequests(eq(Set.of("PENDING_BRANCH", "PENDING_HEAD")), eq(0L),
            eq(ApproverRefresher.CHUNK));
        verify(access).markStage(1L, "PENDING_HEAD", "HEAD", "DEPARTMENT_HEAD", 50L, null);
    }

    @Test
    void aConflictingRowIsSkippedAndTheNextOneIsStillRefreshed() {
        when(access.openRequests(any(), anyLong(), anyInt())).thenReturn(List.of(atHead(1, 40L), atHead(2, 40L)));
        doThrow(new OptimisticLockingFailureException("version"))
            .when(access).markStage(eq(1L), any(), any(), any(), any(), any());

        assertDoesNotThrow(() -> refresher.onOrganisationChanged(new OrganisationChanged()));

        verify(access).markStage(2L, "PENDING_HEAD", "HEAD", "DEPARTMENT_HEAD", 50L, null);
    }

    @Test
    void aNonConflictFailureOnOneRowDoesNotStopTheOtherRows() {
        when(access.openRequests(any(), anyLong(), anyInt())).thenReturn(List.of(atHead(1, 40L), atHead(2, 40L)));
        doThrow(new IllegalStateException("db down"))
            .when(access).markStage(eq(1L), any(), any(), any(), any(), any());

        assertDoesNotThrow(() -> refresher.refresh());

        verify(access).markStage(2L, "PENDING_HEAD", "HEAD", "DEPARTMENT_HEAD", 50L, null);
    }

    @Test
    void anUnexpectedFailureAfterTheCommitIsLoggedNotThrownAtTheCaller() {
        when(access.openRequests(any(), anyLong(), anyInt())).thenThrow(new IllegalStateException("db down"));
        TransactionSynchronizationManager.initSynchronization();
        refresher.onOrganisationChanged(new OrganisationChanged());

        assertDoesNotThrow(() -> TransactionSynchronizationManager.getSynchronizations()
            .forEach(TransactionSynchronization::afterCommit));
    }

    @Test
    void aRowThatMovedOnOrIsAlreadyRightIsNotWritten() {
        when(access.openRequests(any(), anyLong(), anyInt())).thenReturn(List.of(atHead(1, 40L), atHead(2, 50L)));
        when(access.stage(1L)).thenReturn(Optional.of("PENDING_GM")); // approved meanwhile

        refresher.onOrganisationChanged(new OrganisationChanged());

        verify(access, never()).markStage(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void theHeadOfADepartmentIsLookedUpOncePerRefreshNotPerRequest() {
        when(access.openRequests(any(), anyLong(), anyInt())).thenReturn(List.of(atHead(1, 40L), atHead(2, 40L)));

        refresher.onOrganisationChanged(new OrganisationChanged());

        verify(organisation, times(1)).headOf(DEPT);
        verify(access).markStage(1L, "PENDING_HEAD", "HEAD", "DEPARTMENT_HEAD", 50L, null);
        verify(access).markStage(2L, "PENDING_HEAD", "HEAD", "DEPARTMENT_HEAD", 50L, null);
    }

    @Test
    void requestsAreReadInChunksAfterTheLastIdSeen() {
        List<OpenRequest> full = LongStream.rangeClosed(1, ApproverRefresher.CHUNK).mapToObj(i -> atHead(i, 50L)).toList();
        when(access.openRequests(any(), eq(0L), anyInt())).thenReturn(full);
        when(access.openRequests(any(), eq((long) ApproverRefresher.CHUNK), anyInt()))
            .thenReturn(List.of(atHead(ApproverRefresher.CHUNK + 1, 40L)));

        refresher.onOrganisationChanged(new OrganisationChanged());

        verify(access, times(2)).openRequests(any(), anyLong(), anyInt());
        verify(access).markStage(ApproverRefresher.CHUNK + 1, "PENDING_HEAD", "HEAD", "DEPARTMENT_HEAD", 50L, null);
    }
}
