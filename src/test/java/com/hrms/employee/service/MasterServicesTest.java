package com.hrms.employee.service;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.employee.dto.request.BankBranchRequest;
import com.hrms.employee.dto.response.BankBranchResponse;
import com.hrms.employee.entity.Bank;
import com.hrms.employee.entity.BankBranch;
import com.hrms.employee.repository.BankBranchRepository;
import com.hrms.employee.repository.BankRepository;
import com.hrms.employee.service.impl.BankBranchServiceImpl;
import com.hrms.employee.service.impl.BankServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The V5 masters share one shape; Bank (has branches) and BankBranch (has a parent) cover both variants. */
@ExtendWith(MockitoExtension.class)
class MasterServicesTest {

    @Mock BankRepository bankRepository;
    @Mock BankBranchRepository branchRepository;
    @Mock AuditTrail audit;
    @Mock EntityManager entityManager;

    private BankBranchRequest branchRequest() {
        return BankBranchRequest.builder().bankId(1L).code(" br1 ").name(" Main ").nameAr(" الرئيسي ").build();
    }

    private Bank bank(boolean active) {
        return Bank.builder().id(1L).code("B1").name("Bank").nameAr("بنك").isActive(active).version(0L).build();
    }

    @Test
    void branch_create_normalisesCodeAndDefaultsToActive() {
        BankBranchServiceImpl service = new BankBranchServiceImpl(branchRepository, bankRepository, audit, entityManager);
        when(branchRepository.existsByCodeIgnoreCase("BR1")).thenReturn(false);
        when(bankRepository.findById(1L)).thenReturn(Optional.of(bank(true)));
        when(branchRepository.save(any(BankBranch.class))).thenAnswer(i -> i.getArgument(0));

        BankBranchResponse out = service.create(branchRequest());

        assertEquals("BR1", out.getCode());
        assertEquals(1L, out.getBankId());
        assertTrue(out.getIsActive());
    }

    @Test
    void branch_create_rejectsDuplicateCodeAndInactiveBank() {
        BankBranchServiceImpl service = new BankBranchServiceImpl(branchRepository, bankRepository, audit, entityManager);
        when(branchRepository.existsByCodeIgnoreCase("BR1")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.create(branchRequest()));

        when(branchRepository.existsByCodeIgnoreCase("BR1")).thenReturn(false);
        when(bankRepository.findById(1L)).thenReturn(Optional.of(bank(false)));
        assertThrows(BusinessRuleException.class, () -> service.create(branchRequest()));
        verify(branchRepository, never()).save(any());
    }

    @Test
    void bank_cannotBeDeactivatedOrDeletedWhileItHasBranches() {
        BankServiceImpl service = new BankServiceImpl(bankRepository, branchRepository, audit, entityManager);
        when(bankRepository.findById(1L)).thenReturn(Optional.of(bank(true)));
        when(branchRepository.existsByBankIdAndIsActive(1L, true)).thenReturn(true);
        when(branchRepository.existsByBankId(1L)).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.deactivate(1L));
        assertThrows(BusinessRuleException.class, () -> service.delete(1L));
        verify(bankRepository, never()).delete(any());
    }

    private BankBranch branch(Long id, boolean active) {
        return BankBranch.builder().id(id).bankId(1L).code("BR1").name("Main").nameAr("x")
            .isActive(active).version(0L).build();
    }

    private BankBranchServiceImpl branchService() {
        return new BankBranchServiceImpl(branchRepository, bankRepository, audit, entityManager);
    }

    @Test
    void branch_activate_needsActiveBank() {
        when(branchRepository.findById(7L)).thenReturn(Optional.of(branch(7L, false)));
        when(bankRepository.findById(1L)).thenReturn(Optional.of(bank(false)));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> branchService().activate(7L));
        assertEquals("PARENT_INACTIVE", ex.getRuleCode());
        verify(branchRepository, never()).save(any());
    }

    @Test
    void branch_activate_succeedsUnderActiveBank_andDeactivateNeverLooksAtTheBank() {
        when(branchRepository.findById(7L)).thenReturn(Optional.of(branch(7L, false)));
        when(bankRepository.findById(1L)).thenReturn(Optional.of(bank(true)));
        branchService().activate(7L);
        verify(audit).record(eq("BANK_BRANCH_ACTIVATED"), eq("BANK_BRANCH"), any(), anyString());

        when(branchRepository.findById(8L)).thenReturn(Optional.of(branch(8L, true)));
        branchService().deactivate(8L);
        verify(bankRepository, times(1)).findById(1L); // only the activate looked
    }

    @Test
    void branch_update_activeUnderInactiveBankIsRejected_butSwitchingItOffIsAllowed() {
        when(branchRepository.findById(7L)).thenReturn(Optional.of(branch(7L, true)));
        when(bankRepository.findById(1L)).thenReturn(Optional.of(bank(false)));
        BankBranchRequest r = branchRequest();
        r.setVersion(0L);

        assertThrows(BusinessRuleException.class, () -> branchService().update(7L, r));

        r.setIsActive(false);
        when(branchRepository.save(any(BankBranch.class))).thenAnswer(i -> i.getArgument(0));
        assertFalse(branchService().update(7L, r).getIsActive());
    }

    @Test
    void branch_update_requiresCurrentVersion() {
        when(branchRepository.findById(7L)).thenReturn(Optional.of(branch(7L, true)));
        BankBranchRequest r = branchRequest();

        assertThrows(BadRequestException.class, () -> branchService().update(7L, r)); // no version: 400
        r.setVersion(5L);
        assertThrows(OptimisticLockingFailureException.class, () -> branchService().update(7L, r)); // stale: 409
    }
}
