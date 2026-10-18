package com.hrms.employee.service;

import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.employee.DatabaseTest;
import com.hrms.employee.dto.request.BankBranchRequest;
import com.hrms.employee.dto.request.BankRequest;
import com.hrms.employee.dto.request.EmployeeBankAccountRequest;
import com.hrms.employee.dto.response.BankBranchResponse;
import com.hrms.employee.dto.response.BankResponse;
import com.hrms.employee.dto.response.EmployeeBankAccountResponse;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The bank masters and the employee bank-account service against a real Oracle schema built from db/migration
 * (V5, V6): column names, defaults, the one-primary unique index, the foreign keys and the audit-trail writes.
 * Each step ends with flush and clear, because a real request starts with an empty persistence context.
 */
@Transactional
class BankMastersAndAccountsDbTest extends DatabaseTest {

    @Autowired BankService bankService;
    @Autowired BankBranchService branchService;
    @Autowired EmployeeBankAccountService accountService;
    @Autowired EmployeeRepository employeeRepository;
    @PersistenceContext EntityManager em;

    private void fresh() {
        em.flush();
        em.clear();
    }

    private BankResponse bank(String code) {
        BankResponse b = bankService.create(BankRequest.builder().code(code).name("Bank " + code).nameAr("بنك " + code).build());
        fresh();
        return b;
    }

    private Long employee() {
        Employee e = employeeRepository.saveAndFlush(Employee.builder()
            .firstName("Amira").firstNameAr("ع").lastName("Nasser").lastNameAr("ل")
            .dateOfBirth(LocalDate.of(1990, 5, 1)).gender(Gender.FEMALE)
            .personalEmail("bank-test-p@example.test").workEmail("bank-test-w@example.test")
            .hireDate(LocalDate.of(2024, 1, 15)).employmentType(EmploymentType.FULL_TIME).build());
        fresh();
        return e.getId();
    }

    private EmployeeBankAccountRequest account(Long bankId, String number, boolean primary) {
        return EmployeeBankAccountRequest.builder().bankId(bankId).accountNumber(number).isPrimary(primary).build();
    }

    private long primaries(Long employeeId) {
        return ((Number) em.createNativeQuery(
            "select count(*) from HRMS.EMPLOYEE_BANK_ACCOUNTS where EMPLOYEE_ID = ?1 and IS_PRIMARY = 1 and IS_ACTIVE = 1")
            .setParameter(1, employeeId).getSingleResult()).longValue();
    }

    @Test
    void masters_saveWithDatabaseDefaults_enforceParentAndChildRules_andRefuseDeletingABankInUse() {
        BankResponse bank = bank("TB1");
        assertNotNull(bank.getId());
        assertNotNull(bank.getCreatedAt(), "CREATED_AT comes from the column default and is read back");
        assertTrue(bank.getIsActive());
        assertEquals(0L, bank.getVersion());

        BankBranchResponse branch = branchService.create(BankBranchRequest.builder()
            .bankId(bank.getId()).code("TBR1").name("Main").nameAr("الرئيسي").build());
        fresh();
        assertEquals(bank.getId(), branch.getBankId());

        assertThrows(DuplicateResourceException.class, () -> bankService.create(
            BankRequest.builder().code("tb1").name("Other").nameAr("آخر").build()));

        assertEquals(1, bankService.getActiveLookup().stream().filter(l -> l.getId().equals(bank.getId())).count());
        assertEquals(1, branchService.getActiveLookupByBankId(bank.getId()).size());

        assertEquals("MASTER_HAS_ACTIVE_CHILDREN",
            assertThrows(BusinessRuleException.class, () -> bankService.deactivate(bank.getId())).getRuleCode());
        branchService.deactivate(branch.getId());
        fresh();
        bankService.deactivate(bank.getId()); // allowed now: no active branch is left
        fresh();
        assertEquals("PARENT_INACTIVE",
            assertThrows(BusinessRuleException.class, () -> branchService.activate(branch.getId())).getRuleCode());
    }

    @Test
    void accounts_oneActivePrimary_duplicatesBlocked_andABankWithAccountsCannotBeDeleted() {
        BankResponse bank = bank("TB2");
        Long emp = employee();

        EmployeeBankAccountResponse first = accountService.addAccount(emp, account(bank.getId(), "1111 2222", false));
        fresh();
        assertTrue(first.getIsPrimary(), "the first active account becomes primary");

        EmployeeBankAccountResponse second = accountService.addAccount(emp, account(bank.getId(), "3333", true));
        fresh();
        assertEquals(1, primaries(emp), "the unique index allows one active primary; the old one was un-set first");

        assertEquals("DUPLICATE_BANK_ACCOUNT", assertThrows(BusinessRuleException.class,
            () -> accountService.addAccount(emp, account(bank.getId(), "11112222", false))).getRuleCode());
        fresh();

        accountService.setPrimary(emp, first.getId());
        fresh();
        assertEquals(1, primaries(emp));
        assertEquals("CANNOT_DEACTIVATE_PRIMARY", assertThrows(BusinessRuleException.class,
            () -> accountService.deactivateAccount(emp, first.getId())).getRuleCode());
        fresh();

        accountService.deactivateAccount(emp, second.getId());
        fresh();
        // a deactivated account still holds its number: the table's unique key covers every row
        assertEquals("DUPLICATE_BANK_ACCOUNT", assertThrows(BusinessRuleException.class,
            () -> accountService.addAccount(emp, account(bank.getId(), "3333", false))).getRuleCode());
        fresh();

        var listed = accountService.getAccounts(emp);
        assertEquals(2, listed.size());
        assertTrue(listed.get(0).getIsPrimary());
        assertEquals("Bank TB2", listed.get(0).getBankName());
        assertTrue(listed.get(0).getMasked(), "no signed-in user in this test, so the numbers are masked");
        assertEquals("****2222", listed.get(0).getAccountNumber());

        EmployeeBankAccountRequest edit = account(bank.getId(), "11112222", false);
        edit.setAccountHolderName("Amira Nasser");
        edit.setVersion(listed.get(0).getVersion());
        assertEquals("Amira Nasser", accountService.updateAccount(emp, first.getId(), edit).getAccountHolderName());
        fresh();

        // the bank is referenced by employee accounts: the foreign key refuses the delete, reported as "in use"
        assertEquals("MASTER_IN_USE", assertThrows(BusinessRuleException.class,
            () -> bankService.delete(bank.getId())).getRuleCode());
    }
}
