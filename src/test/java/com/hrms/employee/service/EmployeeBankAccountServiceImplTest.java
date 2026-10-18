package com.hrms.employee.service;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.EmployeeBankAccountRequest;
import com.hrms.employee.dto.response.EmployeeBankAccountResponse;
import com.hrms.employee.entity.Bank;
import com.hrms.employee.entity.BankBranch;
import com.hrms.employee.entity.EmployeeBankAccount;
import com.hrms.employee.repository.BankBranchRepository;
import com.hrms.employee.repository.BankRepository;
import com.hrms.employee.repository.EmployeeBankAccountRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.impl.EmployeeBankAccountServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeBankAccountServiceImplTest {

    @Mock EmployeeBankAccountRepository accountRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock BankRepository bankRepository;
    @Mock BankBranchRepository branchRepository;
    @Mock EmployeeAccessGuard accessGuard;
    @Mock AuditTrail audit;
    @Mock EntityManager entityManager;

    EmployeeBankAccountServiceImpl service;

    // A valid IBAN (the ISO 13616 example) and the same number with a wrong check digit.
    static final String IBAN = "GB82 WEST 1234 5698 7654 32";
    static final String BAD_IBAN = "GB83 WEST 1234 5698 7654 32";

    @BeforeEach
    void setUp() {
        service = new EmployeeBankAccountServiceImpl(accountRepository, employeeRepository, bankRepository,
            branchRepository, accessGuard, audit, entityManager);
    }

    private Bank bank(boolean active) {
        return Bank.builder().id(1L).code("B1").name("Bank").nameAr("x").isActive(active).version(0L).build();
    }

    private BankBranch branch(Long id, Long bankId, boolean active) {
        return BankBranch.builder().id(id).bankId(bankId).code("BR" + id).name("Branch").nameAr("x")
            .isActive(active).version(0L).build();
    }

    private EmployeeBankAccount account(Long id, boolean primary, boolean active) {
        return EmployeeBankAccount.builder().id(id).employeeId(10L).bankId(1L).accountNumber("123456789")
            .isPrimary(primary).isActive(active).version(0L).build();
    }

    private EmployeeBankAccountRequest request() {
        return EmployeeBankAccountRequest.builder().bankId(1L).accountNumber(" 1234 5678-9 ").build();
    }

    private void employeeAndBank(boolean bankActive) {
        when(employeeRepository.existsById(10L)).thenReturn(true);
        when(bankRepository.findById(1L)).thenReturn(Optional.of(bank(bankActive)));
    }

    @Test
    void add_firstActiveAccountBecomesPrimary_numberIsNormalised_andAuditNeverHoldsTheNumber() {
        employeeAndBank(true);
        when(accountRepository.countByEmployeeIdAndIsActive(10L, true)).thenReturn(0L);
        when(accountRepository.save(any(EmployeeBankAccount.class))).thenAnswer(i -> i.getArgument(0));

        EmployeeBankAccountRequest r = request();
        r.setIban(IBAN);
        EmployeeBankAccountResponse out = service.addAccount(10L, r);

        assertEquals("12345678-9", out.getAccountNumber());
        assertEquals("GB82WEST12345698765432", out.getIban());
        assertTrue(out.getIsPrimary());
        verify(accountRepository).unsetAllPrimary(10L);
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq("EMPLOYEE_BANK_ACCOUNT_ADDED"), eq("EMPLOYEE_BANK_ACCOUNT"), any(), detail.capture());
        assertFalse(detail.getValue().contains("12345678"), "the audit trail must not hold the account number");
        assertFalse(detail.getValue().contains("GB82"), "nor the IBAN");
    }

    @Test
    void add_secondAccountIsNotPrimaryUnlessAsked() {
        employeeAndBank(true);
        when(accountRepository.countByEmployeeIdAndIsActive(10L, true)).thenReturn(1L);
        when(accountRepository.save(any(EmployeeBankAccount.class))).thenAnswer(i -> i.getArgument(0));

        assertFalse(service.addAccount(10L, request()).getIsPrimary());
        verify(accountRepository, never()).unsetAllPrimary(any());
    }

    @Test
    void add_rejectsUnknownEmployeeInactiveBankBadIbanDuplicateAndForeignBranch() {
        when(employeeRepository.existsById(99L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.addAccount(99L, request()));

        employeeAndBank(false);
        assertEquals("BANK_INACTIVE", rule(() -> service.addAccount(10L, request())));

        employeeAndBank(true);
        EmployeeBankAccountRequest badIban = request();
        badIban.setIban(BAD_IBAN);
        assertEquals("INVALID_IBAN", rule(() -> service.addAccount(10L, badIban)));

        EmployeeBankAccountRequest otherBank = request();
        otherBank.setBankBranchId(5L);
        when(branchRepository.findById(5L)).thenReturn(Optional.of(branch(5L, 2L, true)));
        assertEquals("BRANCH_BANK_MISMATCH", rule(() -> service.addAccount(10L, otherBank)));

        when(accountRepository.existsByBankIdAndAccountNumber(1L, "12345678-9")).thenReturn(true);
        assertEquals("DUPLICATE_BANK_ACCOUNT", rule(() -> service.addAccount(10L, request())));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void add_rejectsAnInactiveBranch() {
        employeeAndBank(true);
        EmployeeBankAccountRequest r = request();
        r.setBankBranchId(5L);
        when(branchRepository.findById(5L)).thenReturn(Optional.of(branch(5L, 1L, false)));

        assertEquals("BRANCH_INACTIVE", rule(() -> service.addAccount(10L, r)));
    }

    @Test
    void update_requiresCurrentVersion_andRefusesADeactivatedAccount() {
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, true, true)));
        EmployeeBankAccountRequest r = request();

        assertThrows(BadRequestException.class, () -> service.updateAccount(10L, 7L, r)); // no version: 400
        r.setVersion(3L);
        assertThrows(OptimisticLockingFailureException.class, () -> service.updateAccount(10L, 7L, r)); // stale: 409

        when(accountRepository.findById(8L)).thenReturn(Optional.of(account(8L, false, false)));
        r.setVersion(0L);
        assertEquals("ACCOUNT_INACTIVE", rule(() -> service.updateAccount(10L, 8L, r)));
    }

    @Test
    void update_isNotFoundForAnotherEmployeesAccount() {
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, true, true)));
        EmployeeBankAccountRequest r = request();
        r.setVersion(0L);

        assertThrows(ResourceNotFoundException.class, () -> service.updateAccount(11L, 7L, r));
    }

    @Test
    void update_canPromoteButNeverDemote() {
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, false, true)));
        when(accountRepository.save(any(EmployeeBankAccount.class))).thenAnswer(i -> i.getArgument(0));
        when(bankRepository.findAllById(any())).thenReturn(List.of(bank(true)));
        EmployeeBankAccountRequest r = request();
        r.setVersion(0L);
        r.setIsPrimary(true);

        assertTrue(service.updateAccount(10L, 7L, r).getIsPrimary());
        verify(accountRepository).unsetAllPrimary(10L);

        when(accountRepository.findById(8L)).thenReturn(Optional.of(account(8L, true, true)));
        r.setIsPrimary(false);
        assertTrue(service.updateAccount(10L, 8L, r).getIsPrimary());
    }

    @Test
    void deactivate_primaryIsRefusedWhileOthersAreActive_butTheLastAccountCanGo() {
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, true, true)));
        when(accountRepository.countByEmployeeIdAndIsActive(10L, true)).thenReturn(2L);
        assertEquals("CANNOT_DEACTIVATE_PRIMARY", rule(() -> service.deactivateAccount(10L, 7L)));
        verify(accountRepository, never()).save(any());

        when(accountRepository.countByEmployeeIdAndIsActive(10L, true)).thenReturn(1L);
        service.deactivateAccount(10L, 7L);
        verify(accountRepository).save(argThat(a -> !a.getIsActive() && !a.getIsPrimary()));
        verify(audit).record(eq("EMPLOYEE_BANK_ACCOUNT_DEACTIVATED"), eq("EMPLOYEE_BANK_ACCOUNT"), any(), anyString());
    }

    @Test
    void setPrimary_movesThePrimaryFlag_andRefusesADeactivatedAccount() {
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, false, true)));
        service.setPrimary(10L, 7L);
        verify(accountRepository).unsetAllPrimary(10L);
        verify(accountRepository).save(argThat(EmployeeBankAccount::getIsPrimary));

        when(accountRepository.findById(8L)).thenReturn(Optional.of(account(8L, false, false)));
        assertEquals("ACCOUNT_INACTIVE", rule(() -> service.setPrimary(10L, 8L)));

        // already primary: nothing to do, and above all not un-set by the bulk update
        when(accountRepository.findById(9L)).thenReturn(Optional.of(account(9L, true, true)));
        clearInvocations(accountRepository);
        service.setPrimary(10L, 9L);
        verify(accountRepository, never()).unsetAllPrimary(any());
    }

    @Test
    void get_masksNumbersUnlessTheCallerMaySeeThem() {
        when(employeeRepository.existsById(10L)).thenReturn(true);
        EmployeeBankAccount a = account(7L, true, true);
        a.setIban("GB82WEST12345698765432");
        when(accountRepository.findByEmployeeIdOrderByIdAsc(10L)).thenReturn(List.of(a));
        when(bankRepository.findAllById(any())).thenReturn(List.of(bank(true)));

        when(accessGuard.canViewUnmasked(10L)).thenReturn(false);
        EmployeeBankAccountResponse masked = service.getAccounts(10L).get(0);
        assertEquals("*****6789", masked.getAccountNumber());
        assertEquals("******************5432", masked.getIban());
        assertTrue(masked.getMasked());

        when(accessGuard.canViewUnmasked(10L)).thenReturn(true);
        EmployeeBankAccountResponse full = service.getAccounts(10L).get(0);
        assertEquals("123456789", full.getAccountNumber());
        assertFalse(full.getMasked());
        assertEquals("Bank", full.getBankName());
    }

    @Test
    void get_listsActiveFirstAndPrimaryFirst() {
        when(employeeRepository.existsById(10L)).thenReturn(true);
        when(accountRepository.findByEmployeeIdOrderByIdAsc(10L)).thenReturn(List.of(
            account(1L, false, false), account(2L, false, true), account(3L, true, true)));
        when(bankRepository.findAllById(any())).thenReturn(List.of(bank(true)));

        List<Long> ids = service.getAccounts(10L).stream().map(EmployeeBankAccountResponse::getId).toList();
        assertEquals(List.of(3L, 2L, 1L), ids);
    }

    private static String rule(org.junit.jupiter.api.function.Executable call) {
        return assertThrows(BusinessRuleException.class, call).getRuleCode();
    }
}
