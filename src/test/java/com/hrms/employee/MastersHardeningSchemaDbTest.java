package com.hrms.employee;

import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V26 against a real Oracle schema built from db/migration: leave type codes are a real foreign key (no hard-coded list),
 * the seven older masters carry a VERSION column, their text columns count characters, and every foreign key has an index.
 */
@Transactional
class MastersHardeningSchemaDbTest extends DatabaseTest {

    private static final List<String> OLDER_MASTERS = List.of("DOCUMENT_TYPES", "HOLIDAY_CALENDAR", "LEAVE_TYPES",
        "OFFICE_LOCATIONS", "SALARY_COMPONENTS", "SALARY_STRUCTURES", "WORK_SHIFTS");

    @Autowired JdbcTemplate jdbc;
    @Autowired EmployeeRepository employees;

    private long employee() {
        return employees.saveAndFlush(Employee.builder()
            .firstName("Akol").firstNameAr("ع").lastName("Mayen").lastNameAr("ل")
            .dateOfBirth(LocalDate.of(1990, 5, 4)).gender(Gender.FEMALE)
            .personalEmail("v26-p@example.test").workEmail("v26-w@example.test")
            .hireDate(LocalDate.of(2024, 2, 1)).employmentType(EmploymentType.FULL_TIME).build()).getId();
    }

    private void leaveType(String code) {
        jdbc.update("insert into HRMS.LEAVE_TYPES (LEAVE_TYPE_ID, CODE, NAME_EN, NAME_AR) values (?, ?, ?, N'اختبار')",
            code.hashCode() & 0xFFFFF | 0x100000, code, "Name " + code);
    }

    private void request(long id, long employeeId, String code) {
        jdbc.update("insert into HRMS.LEAVE_REQUESTS (LEAVE_REQ_ID, CREATED_AT, UPDATED_AT, EMPLOYEE_CODE, EMPLOYEE_ID, "
            + "END_DATE, IS_ACTIVE, LEAVE_TYPE, START_DATE, STATUS, TOTAL_DAYS) "
            + "values (?, systimestamp, systimestamp, 'E-V26', ?, date '2026-03-04', true, ?, date '2026-03-02', 'PENDING', 3)",
            id, employeeId, code);
    }

    private void balance(long id, long employeeId, String code) {
        jdbc.update("insert into HRMS.LEAVE_BALANCES (BALANCE_ID, CREATED_AT, UPDATED_AT, EMPLOYEE_ID, LEAVE_TYPE, "
            + "PENDING_DAYS, TOTAL_DAYS, USED_DAYS, YEAR) values (?, systimestamp, systimestamp, ?, ?, 0, 10, 0, 2026)",
            id, employeeId, code);
    }

    @Test
    void aRequestAndABalanceCanUseEveryLeaveTypeIncludingOnesAddedLater() {
        long emp = employee();
        // HAJJ, BEREAVEMENT and STUDY are active leave types that the old hard-coded list on LEAVE_REQUESTS refused.
        leaveType("HAJJ");
        leaveType("STUDY");
        request(990001, emp, "HAJJ");
        request(990002, emp, "STUDY");
        leaveType("V26_NEW");
        request(990003, emp, "V26_NEW");
        balance(990001, emp, "V26_NEW");
        assertEquals(1, jdbc.queryForObject("select count(*) from HRMS.LEAVE_REQUESTS where LEAVE_TYPE = 'V26_NEW'", Integer.class));
    }

    @Test
    void anUnknownLeaveTypeCodeIsRefusedByTheDatabase() {
        long emp = employee();
        assertThrows(DataIntegrityViolationException.class, () -> request(990004, emp, "NO_SUCH_TYPE"));
        assertThrows(DataIntegrityViolationException.class, () -> balance(990002, emp, "NO_SUCH_TYPE"));
    }

    @Test
    void aLeaveTypeCodeThatIsInUseCannotBeRenamed() {
        long emp = employee();
        leaveType("V26_USED");
        request(990005, emp, "V26_USED");
        assertThrows(DataIntegrityViolationException.class,
            () -> jdbc.update("update HRMS.LEAVE_TYPES set CODE = 'V26_RENAMED' where CODE = 'V26_USED'"));
    }

    @Test
    void theSevenOlderMastersHaveAVersionColumn() {
        for (String table : OLDER_MASTERS) {
            assertEquals(1, jdbc.queryForObject(
                "select count(*) from user_tab_columns where table_name = ? and column_name = 'VERSION'", Integer.class, table),
                table + " has no VERSION column");
        }
    }

    @Test
    void textColumnsOfTheOlderMastersCountCharactersNotBytes() {
        List<String> tables = List.of("DEPARTMENTS", "DESIGNATIONS", "DOCUMENT_TYPES", "HOLIDAY_CALENDAR", "LEAVE_TYPES",
            "OFFICE_LOCATIONS", "SALARY_COMPONENTS", "SALARY_STRUCTURES", "WORK_SHIFTS");
        List<String> bytes = jdbc.queryForList("select table_name || '.' || column_name from user_tab_columns "
            + "where data_type = 'VARCHAR2' and char_used = 'B' and table_name in ("
            + String.join(",", tables.stream().map(t -> "'" + t + "'").toList()) + ")", String.class);
        assertTrue(bytes.isEmpty(), "byte-length columns left: " + bytes);
    }

    @Test
    void everyForeignKeyOfTheEmployeeSchemaHasAnIndexOnItsFirstColumn() {
        List<String> unindexed = jdbc.queryForList(
            "select c.table_name || '.' || cc.column_name from user_constraints c "
                + "join user_cons_columns cc on cc.constraint_name = c.constraint_name and cc.position = 1 "
                + "where c.constraint_type = 'R' and c.table_name not like 'INVOICE%' "
                + "and not exists (select 1 from user_ind_columns i where i.table_name = c.table_name "
                + "and i.column_name = cc.column_name and i.column_position = 1)", String.class);
        assertTrue(unindexed.isEmpty(), "foreign keys without an index: " + unindexed);
    }

    @Test
    void anOfficeLocationInsertedWithoutCountryOrTimeZoneDefaultsToSouthSudan() {
        jdbc.update("insert into HRMS.OFFICE_LOCATIONS (LOCATION_ID, LOCATION_CODE, LOCATION_NAME, LOCATION_NAME_AR, "
            + "ADDRESS_LINE1, CITY) values (990101, 'V26_LOC', 'V26 location', N'اختبار', 'Road', 'Juba')");
        assertEquals("South Sudan", jdbc.queryForObject("select COUNTRY from HRMS.OFFICE_LOCATIONS where LOCATION_ID = 990101", String.class));
        assertEquals("Africa/Juba", jdbc.queryForObject("select TIMEZONE from HRMS.OFFICE_LOCATIONS where LOCATION_ID = 990101", String.class));
    }
}
