package com.hrms.employee;

import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.payroll.repository.EmployeeSalaryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * On real Oracle: closing the current salary ends it on the given last day (not today), and
 * db/fix_salary_end_dates.sql repairs history written by the old rule (end = the next record's start - 1).
 */
@Transactional
class EmployeeSalaryCloseDbTest extends DatabaseTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired EmployeeRepository employees;
    @Autowired EmployeeSalaryRepository salaries;

    private long employee(String key) {
        return employees.saveAndFlush(Employee.builder()
            .firstName("Close" + key).firstNameAr("ا").lastName("Test").lastNameAr("ت")
            .dateOfBirth(LocalDate.of(1990, 1, 1)).gender(Gender.FEMALE)
            .personalEmail("close-p-" + key + "@example.test").workEmail("close-w-" + key + "@example.test")
            .hireDate(LocalDate.of(2024, 1, 1)).employmentType(EmploymentType.FULL_TIME).build()).getId();
    }

    private void structure() {
        jdbc.update("insert into HRMS.SALARY_STRUCTURES (STRUCTURE_ID, STRUCTURE_CODE, STRUCTURE_NAME, VERSION) "
            + "values (993301, 'CLOSE_S', 'Close structure', 0)");
    }

    private void salary(long id, long employeeId, String from, String to, int current) {
        jdbc.update("insert into HRMS.EMPLOYEE_SALARY (EMP_SALARY_ID, EMPLOYEE_ID, STRUCTURE_ID, BASIC_SALARY, GROSS_SALARY, "
            + "NET_SALARY, EFFECTIVE_FROM, EFFECTIVE_TO, IS_CURRENT) values (?, ?, 993301, 1, 1, 1, date '" + from + "', "
            + (to == null ? "null" : "date '" + to + "'") + ", ?)", id, employeeId, current);
    }

    private String end(long id) {
        List<Map<String, Object>> r = jdbc.queryForList(
            "select to_char(EFFECTIVE_TO, 'YYYY-MM-DD') T from HRMS.EMPLOYEE_SALARY where EMP_SALARY_ID = ?", id);
        return (String) r.get(0).get("T");
    }

    @Test
    void closingEndsTheCurrentRecordOnTheGivenLastDay() {
        structure();
        long e = employee("a");
        salary(993301, e, "2026-11-22", null, 1);

        salaries.deactivateCurrent(e, LocalDate.of(2026, 11, 30));

        assertEquals("2026-11-30", end(993301));
        assertEquals(0, jdbc.queryForObject("select IS_CURRENT from HRMS.EMPLOYEE_SALARY where EMP_SALARY_ID = 993301", Integer.class));
    }

    @Test
    void theRepairScriptEndsEachOldRecordTheDayBeforeTheNextOneStarts() throws Exception {
        structure();
        long gap = employee("gap");       // old rule, future-dated change: ended today, next starts later
        salary(993311, gap, "2026-11-22", "2026-11-23", 0);
        salary(993312, gap, "2026-12-01", null, 1);
        long overlap = employee("ovl");   // old rule, same-day change: ended on the next record's first day
        salary(993321, overlap, "2026-03-01", "2026-11-22", 0);
        salary(993322, overlap, "2026-11-22", null, 1);
        long fine = employee("ok");       // already right: untouched
        salary(993331, fine, "2026-01-01", "2026-05-31", 0);
        salary(993332, fine, "2026-06-01", null, 1);

        String script = Files.readString(Path.of("db", "fix_salary_end_dates.sql")).replaceAll("(?m)^--.*$", "");
        int changed = Arrays.stream(script.split(";\\s*\\n")).map(String::trim)
            .filter(s -> !s.isEmpty() && !s.equalsIgnoreCase("COMMIT")).mapToInt(jdbc::update).sum();

        assertEquals(2, changed);
        assertEquals("2026-11-30", end(993311));
        assertEquals("2026-11-21", end(993321));
        assertEquals("2026-05-31", end(993331));
        assertEquals(null, end(993312));
    }
}
