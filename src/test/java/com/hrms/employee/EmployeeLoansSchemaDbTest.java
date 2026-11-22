package com.hrms.employee;

import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.payroll.entity.EmployeeLoan.LoanStatus;
import com.hrms.payroll.repository.EmployeeLoanRepository;
import com.hrms.payroll.repository.projection.EmployeeLoanRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V33 against a real Oracle schema: one ACTIVE loan per employee and type (ended ones stay as history), the checks,
 * the register search with its filters, and the monthly total of running loans.
 */
@Transactional
class EmployeeLoansSchemaDbTest extends DatabaseTest {

    private static final long TYPE = 993301;
    private static final long OTHER_TYPE = 993302;

    @Autowired JdbcTemplate jdbc;
    @Autowired EmployeeRepository employees;
    @Autowired EmployeeLoanRepository loans;

    private Long employee(String suffix, String first) {
        return employees.saveAndFlush(Employee.builder()
            .firstName(first).firstNameAr("ا").lastName("Majok").lastNameAr("م")
            .dateOfBirth(LocalDate.of(1990, 1, 1)).gender(Gender.MALE)
            .personalEmail("v33-p" + suffix + "@example.test").workEmail("v33-w" + suffix + "@example.test")
            .hireDate(LocalDate.of(2024, 1, 1)).employmentType(EmploymentType.FULL_TIME).build()).getId();
    }

    private void types() {
        for (long id : new long[] {TYPE, OTHER_TYPE}) {
            jdbc.update("insert into HRMS.SALARY_COMPONENTS (COMPONENT_ID, COMPONENT_CODE, COMPONENT_NAME, COMPONENT_NAME_AR, "
                + "COMPONENT_TYPE, CALC_TYPE, IS_LOAN, VERSION) values (?, ?, ?, N'x', 'DEDUCTION', 'FIXED', 1, 0)",
                id, "V33_" + id, "V33 advance " + id);
        }
    }

    private int loan(long id, Long employeeId, long type, String status, String start, int paid) {
        return jdbc.update("insert into HRMS.EMPLOYEE_LOANS (LOAN_ID, EMPLOYEE_ID, COMPONENT_ID, PRINCIPAL, INSTALMENT_COUNT, "
            + "MONTHLY_INSTALMENT, START_MONTH, INSTALMENTS_PAID, STATUS) values (?, ?, ?, 3600, 36, 100, date '" + start
            + "', ?, ?)", id, employeeId, type, paid, status);
    }

    @Test
    void oneActiveLoanPerEmployeeAndType_endedOnesStayAsHistory() {
        types();
        Long a = employee("a", "Deng");
        assertEquals(1, loan(993301, a, TYPE, "ACTIVE", "2026-12-01", 0));
        assertThrows(DataIntegrityViolationException.class, () -> loan(993302, a, TYPE, "ACTIVE", "2027-01-01", 0));
        assertEquals(1, loan(993303, a, OTHER_TYPE, "ACTIVE", "2026-12-01", 0));
        assertEquals(1, loan(993304, a, TYPE, "CLOSED", "2025-01-01", 0));
        assertEquals(1, loan(993305, a, TYPE, "CANCELLED", "2025-01-01", 0));
        assertTrue(loans.existsByEmployeeIdAndComponentIdAndStatus(a, TYPE, LoanStatus.ACTIVE));
    }

    @Test
    void theChecksRefuseAMidMonthStartMorePaidThanOwedAndAnUnknownStatus() {
        types();
        Long a = employee("b", "Ayen");
        assertThrows(DataIntegrityViolationException.class, () -> loan(993311, a, TYPE, "ACTIVE", "2026-12-15", 0));
        assertThrows(DataIntegrityViolationException.class, () -> loan(993312, a, TYPE, "ACTIVE", "2026-12-01", 37));
        assertThrows(DataIntegrityViolationException.class, () -> loan(993313, a, TYPE, "PAUSED", "2026-12-01", 0));
        // A component added without saying so is not a loan type.
        jdbc.update("insert into HRMS.SALARY_COMPONENTS (COMPONENT_ID, COMPONENT_CODE, COMPONENT_NAME, COMPONENT_NAME_AR, "
            + "COMPONENT_TYPE, CALC_TYPE, VERSION) values (993319, 'V33_PLAIN', 'V33 plain', N'x', 'DEDUCTION', 'FIXED', 0)");
        assertEquals(0, jdbc.queryForObject("select IS_LOAN from HRMS.SALARY_COMPONENTS where COMPONENT_ID = 993319", Integer.class));
    }

    @Test
    void theRegisterSearchesByTextTypeStatusAndEmployee_newestFirst() {
        types();
        Long a = employee("c", "Garang");
        Long b = employee("d", "Nyandeng");
        loan(993321, a, TYPE, "ACTIVE", "2026-12-01", 0);
        loan(993322, a, OTHER_TYPE, "CLOSED", "2026-01-01", 5);
        loan(993323, b, TYPE, "ACTIVE", "2026-12-01", 2);

        List<Long> all = ids(loans.search("majok", null, null, null, PageRequest.of(0, 20)).getContent());
        assertEquals(List.of(993323L, 993322L, 993321L), all.stream().filter(i -> i >= 993321).toList());
        assertEquals(List.of(993323L), ids(loans.search("nyandeng", null, null, null, PageRequest.of(0, 20)).getContent()));
        assertEquals(List.of(993322L), ids(loans.search(null, OTHER_TYPE, "CLOSED", a, PageRequest.of(0, 20)).getContent()));

        EmployeeLoanRow row = loans.search(null, null, null, b, PageRequest.of(0, 20)).getContent().get(0);
        assertEquals("V33 advance " + TYPE, row.getLoanTypeName());
        assertEquals("Nyandeng Majok", row.getEmployeeName());
        assertEquals("2026-12-01", row.getStartMonth());
        assertEquals(2, row.getInstalmentsPaid());
    }

    @Test
    void theMonthlyTotalCountsRunningLoansThatHaveStarted() {
        types();
        Long a = employee("e", "Achol");
        loan(993331, a, TYPE, "ACTIVE", "2026-11-01", 0);
        loan(993332, a, OTHER_TYPE, "ACTIVE", "2027-03-01", 0);   // not started yet
        loan(993333, a, TYPE, "CLOSED", "2026-01-01", 0);         // ended
        assertEquals(0, new BigDecimal("100").compareTo(loans.monthlyInstalments(a, LocalDate.of(2026, 12, 1))));
        assertEquals(0, new BigDecimal("200").compareTo(loans.monthlyInstalments(a, LocalDate.of(2027, 3, 1))));
        assertEquals(0, BigDecimal.ZERO.compareTo(loans.monthlyInstalments(a, LocalDate.of(2026, 10, 1))));
    }

    private static List<Long> ids(List<EmployeeLoanRow> rows) {
        return rows.stream().map(EmployeeLoanRow::getLoanId).toList();
    }
}
