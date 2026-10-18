package com.hrms.employee.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.common.util.Strings;
import com.hrms.employee.dto.request.EmployeeBankAccountRequest;
import com.hrms.employee.dto.response.EmployeeBankAccountResponse;
import com.hrms.employee.entity.Bank;
import com.hrms.employee.entity.BankBranch;
import com.hrms.employee.entity.EmployeeBankAccount;
import com.hrms.employee.repository.BankBranchRepository;
import com.hrms.employee.repository.BankRepository;
import com.hrms.employee.repository.EmployeeBankAccountRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.EmployeeBankAccountService;
import com.hrms.employee.service.EmployeeChecks;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Employee bank accounts. Writers are HR_ADMIN only (the controller). The audit trail records who changed which
 * account but never the account number or IBAN.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeBankAccountServiceImpl implements EmployeeBankAccountService {

    private static final String TARGET = "EMPLOYEE_BANK_ACCOUNT";

    private final EmployeeBankAccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final BankRepository bankRepository;
    private final BankBranchRepository branchRepository;
    private final EmployeeAccessGuard accessGuard;
    private final AuditTrail audit;
    private final EntityManager entityManager;

    @Override
    public List<EmployeeBankAccountResponse> getAccounts(Long employeeId) {
        EmployeeChecks.requireExists(employeeRepository, employeeId);
        boolean masked = !accessGuard.canViewUnmasked(employeeId);
        List<EmployeeBankAccount> accounts = accountRepository.findByEmployeeIdOrderByIdAsc(employeeId);
        Names names = namesFor(accounts);
        return accounts.stream()
            .sorted(Comparator.comparing((EmployeeBankAccount a) -> !a.getIsActive())
                .thenComparing(a -> !a.getIsPrimary())
                .thenComparing(EmployeeBankAccount::getId))
            .map(a -> toResponse(a, masked, names))
            .toList();
    }

    @Override
    @Transactional
    public EmployeeBankAccountResponse addAccount(Long employeeId, EmployeeBankAccountRequest request) {
        EmployeeChecks.requireExists(employeeRepository, employeeId);
        String number = normaliseNumber(request.getAccountNumber());
        String iban = normaliseIban(request.getIban());
        requireActiveBank(request.getBankId());
        checkBranch(request.getBankId(), request.getBankBranchId(), null);
        if (accountRepository.existsByBankIdAndAccountNumber(request.getBankId(), number)) throw duplicate();

        boolean primary = Boolean.TRUE.equals(request.getIsPrimary())
            || accountRepository.countByEmployeeIdAndIsActive(employeeId, true) == 0;
        if (primary) accountRepository.unsetAllPrimary(employeeId);

        EmployeeBankAccount entity = EmployeeBankAccount.builder()
            .employeeId(employeeId)
            .bankId(request.getBankId())
            .bankBranchId(request.getBankBranchId())
            .accountNumber(number)
            .iban(iban)
            .accountHolderName(Strings.trimToNull(request.getAccountHolderName()))
            .isPrimary(primary)
            .isActive(true)
            .build();
        EmployeeBankAccount saved = accountRepository.save(entity);
        entityManager.flush();
        log.info("Bank account {} added for employee {}", saved.getId(), employeeId);
        audit.record("EMPLOYEE_BANK_ACCOUNT_ADDED", TARGET, saved.getId(), summary(saved));
        return toResponse(saved, false, namesFor(List.of(saved)));
    }

    @Override
    @Transactional
    public EmployeeBankAccountResponse updateAccount(Long employeeId, Long accountId,
                                                     EmployeeBankAccountRequest request) {
        EmployeeBankAccount existing = findAccount(employeeId, accountId);
        if (request.getVersion() == null) throw new BadRequestException("version is required to update a bank account");
        Versions.requireCurrent(request.getVersion(), existing.getVersion());
        if (!existing.getIsActive()) {
            throw new BusinessRuleException("ACCOUNT_INACTIVE", "A deactivated bank account cannot be edited.");
        }
        String number = normaliseNumber(request.getAccountNumber());
        String iban = normaliseIban(request.getIban());

        boolean bankChanged = !request.getBankId().equals(existing.getBankId());
        if (bankChanged) requireActiveBank(request.getBankId());
        checkBranch(request.getBankId(), request.getBankBranchId(), existing.getBankBranchId());
        if ((bankChanged || !number.equals(existing.getAccountNumber()))
                && accountRepository.existsByBankIdAndAccountNumberAndIdNot(request.getBankId(), number, accountId)) {
            throw duplicate();
        }

        String before = summary(existing);
        existing.setBankId(request.getBankId());
        existing.setBankBranchId(request.getBankBranchId());
        existing.setAccountNumber(number);
        existing.setIban(iban);
        existing.setAccountHolderName(Strings.trimToNull(request.getAccountHolderName()));
        // The form can only promote: another account stays primary until this one is chosen, never silently.
        if (Boolean.TRUE.equals(request.getIsPrimary()) && !existing.getIsPrimary()) {
            accountRepository.unsetAllPrimary(employeeId);
            existing.setIsPrimary(true);
        }
        EmployeeBankAccount saved = accountRepository.save(existing);
        entityManager.flush(); // so the response carries the incremented version
        audit.record("EMPLOYEE_BANK_ACCOUNT_UPDATED", TARGET, accountId, before + " -> " + summary(saved));
        return toResponse(saved, false, namesFor(List.of(saved)));
    }

    @Override
    @Transactional
    public void setPrimary(Long employeeId, Long accountId) {
        EmployeeBankAccount account = findAccount(employeeId, accountId);
        if (!account.getIsActive()) {
            throw new BusinessRuleException("ACCOUNT_INACTIVE", "A deactivated bank account cannot be the primary one.");
        }
        if (account.getIsPrimary()) return;
        accountRepository.unsetAllPrimary(employeeId);
        account.setIsPrimary(true);
        accountRepository.save(account);
        audit.record("EMPLOYEE_BANK_ACCOUNT_PRIMARY", TARGET, accountId, summary(account));
    }

    @Override
    @Transactional
    public void deactivateAccount(Long employeeId, Long accountId) {
        EmployeeBankAccount account = findAccount(employeeId, accountId);
        if (!account.getIsActive()) return;
        // Payroll pays into the primary account. Dropping it while others exist would silently re-route salary.
        if (account.getIsPrimary() && accountRepository.countByEmployeeIdAndIsActive(employeeId, true) > 1) {
            throw new BusinessRuleException("CANNOT_DEACTIVATE_PRIMARY",
                "This is the primary account. Choose another account as primary first.");
        }
        account.setIsActive(false);
        account.setIsPrimary(false);
        accountRepository.save(account);
        audit.record("EMPLOYEE_BANK_ACCOUNT_DEACTIVATED", TARGET, accountId, summary(account));
    }

    // ── helpers ────────────────────────────────────────────────

    private EmployeeBankAccount findAccount(Long employeeId, Long accountId) {
        EmployeeBankAccount account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Bank account", "id", accountId));
        if (!account.getEmployeeId().equals(employeeId)) {
            throw new ResourceNotFoundException("Bank account", "id", accountId);
        }
        return account;
    }

    private void requireActiveBank(Long bankId) {
        Bank bank = bankRepository.findById(bankId)
            .orElseThrow(() -> new ResourceNotFoundException("Bank", "id", bankId));
        if (!Boolean.TRUE.equals(bank.getIsActive())) {
            throw new BusinessRuleException("BANK_INACTIVE", "Bank " + bank.getCode() + " is not active.");
        }
    }

    /** A branch must belong to the chosen bank; a newly chosen branch must also be active. */
    private void checkBranch(Long bankId, Long branchId, Long currentBranchId) {
        if (branchId == null) return;
        BankBranch branch = branchRepository.findById(branchId)
            .orElseThrow(() -> new ResourceNotFoundException("Bank branch", "id", branchId));
        if (!branch.getBankId().equals(bankId)) {
            throw new BusinessRuleException("BRANCH_BANK_MISMATCH", "The branch does not belong to the chosen bank.");
        }
        if (!branchId.equals(currentBranchId) && !Boolean.TRUE.equals(branch.getIsActive())) {
            throw new BusinessRuleException("BRANCH_INACTIVE", "Bank branch " + branch.getCode() + " is not active.");
        }
    }

    private static BusinessRuleException duplicate() {
        return new BusinessRuleException("DUPLICATE_BANK_ACCOUNT",
            "This account number is already registered at this bank.");
    }

    private static String normaliseNumber(String raw) {
        return raw.replace(" ", "").toUpperCase();
    }

    /** Upper-case, no spaces; null when empty. A malformed IBAN is refused. */
    private static String normaliseIban(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String iban = raw.replace(" ", "").toUpperCase();
        if (!Ibans.isValid(iban)) {
            throw new BusinessRuleException("INVALID_IBAN", "The IBAN is not valid. Check the number and try again.");
        }
        return iban;
    }

    /** Ids only: the audit trail must never hold an account number or IBAN. */
    private static String summary(EmployeeBankAccount a) {
        return "employeeId=" + a.getEmployeeId() + ", bankId=" + a.getBankId() + ", branchId=" + a.getBankBranchId()
            + ", primary=" + a.getIsPrimary() + ", active=" + a.getIsActive();
    }

    private record Names(Map<Long, Bank> banks, Map<Long, BankBranch> branches) {}

    private Names namesFor(List<EmployeeBankAccount> accounts) {
        Map<Long, Bank> banks = new HashMap<>();
        bankRepository.findAllById(accounts.stream().map(EmployeeBankAccount::getBankId).collect(Collectors.toSet()))
            .forEach(b -> banks.put(b.getId(), b));
        Map<Long, BankBranch> branches = new HashMap<>();
        branchRepository.findAllById(accounts.stream().map(EmployeeBankAccount::getBankBranchId)
                .filter(Objects::nonNull).collect(Collectors.toSet()))
            .forEach(b -> branches.put(b.getId(), b));
        return new Names(banks, branches);
    }

    /** Last 4 characters only; shorter values are fully masked (same rule as the identity numbers). */
    static String mask(String value) {
        return IdentityInfoServiceImpl.mask(value);
    }

    private EmployeeBankAccountResponse toResponse(EmployeeBankAccount a, boolean masked, Names names) {
        Bank bank = names.banks().get(a.getBankId());
        BankBranch branch = a.getBankBranchId() == null ? null : names.branches().get(a.getBankBranchId());
        return EmployeeBankAccountResponse.builder()
            .id(a.getId())
            .employeeId(a.getEmployeeId())
            .bankId(a.getBankId())
            .bankName(bank == null ? null : bank.getName())
            .bankNameAr(bank == null ? null : bank.getNameAr())
            .bankBranchId(a.getBankBranchId())
            .bankBranchName(branch == null ? null : branch.getName())
            .bankBranchNameAr(branch == null ? null : branch.getNameAr())
            .accountNumber(masked ? mask(a.getAccountNumber()) : a.getAccountNumber())
            .iban(masked ? mask(a.getIban()) : a.getIban())
            .accountHolderName(a.getAccountHolderName())
            .isPrimary(a.getIsPrimary())
            .isActive(a.getIsActive())
            .masked(masked)
            .version(a.getVersion())
            .createdBy(a.getCreatedBy())
            .updatedBy(a.getUpdatedBy())
            .createdAt(a.getCreatedAt() != null ? a.getCreatedAt().toString() : null)
            .updatedAt(a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : null)
            .build();
    }
}
