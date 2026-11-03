package com.hrms.employee;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * db/seed_sample_employees.sql against a real Oracle schema built from db/migration (V1..latest): it loads five employees as the
 * Create Employee form stores them plus a login each, it can run twice without adding anything, the Arabic text survives, the
 * logins use the documented password, and the rollback removes exactly what the seed created.
 */
class SeedSampleEmployeesDbTest extends DatabaseTest {

    private static final String SEED = "db/seed_sample_employees.sql";
    private static final String ROLLBACK = "db/seed_sample_employees_rollback.sql";

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    /** The statements of a plain script: comment lines removed, split on the semicolon that ends a line. */
    private static List<String> statements(String text) {
        return Arrays.stream(text.replaceAll("(?m)^--.*$", "").split(";\\s*\\n"))
            .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private void run(String file) throws Exception {
        List<String> all = statements(Files.readString(Path.of(file)));
        new TransactionTemplate(txManager).executeWithoutResult(s -> all.forEach(jdbc::execute));
    }

    @Test
    void loadsFiveEmployeesWithLogins_isRepeatable_andRollsBack() throws Exception {
        long employeesBefore = count("select count(*) from HRMS.EMPLOYEES");

        run(SEED);

        assertEquals(5, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SAMPLE5'"));
        assertEquals(employeesBefore + 5, count("select count(*) from HRMS.EMPLOYEES"));
        assertEquals(5, count("select count(*) from HRMS.AUTH_USERS where CREATED_BY = 'SAMPLE5'"));
        assertEquals(5, count("select count(distinct EMPLOYEE_CODE) from HRMS.EMPLOYEES where CREATED_BY = 'SAMPLE5'"));
        assertEquals(5, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SAMPLE5' and EMPLOYEE_CODE like 'EMP-2026-%'"));

        // the Create Employee form's three groups of details are all filled
        assertEquals(0, count("""
            select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SAMPLE5'
               and (FIRST_NAME is null or FIRST_NAME_AR is null or LAST_NAME is null or LAST_NAME_AR is null or DATE_OF_BIRTH is null
                 or PLACE_OF_BIRTH is null or GENDER is null or BLOOD_GROUP is null or MARITAL_STATUS is null or NATIONALITY is null
                 or RELIGION is null or PERSONAL_EMAIL is null or WORK_EMAIL is null or PERSONAL_PHONE is null
                 or HIRE_DATE is null or EMPLOYMENT_STATUS is null or EMPLOYMENT_TYPE is null)
            """));
        // a mix: some on probation, some confirmed; more than one employment type; optional parts present for some only
        assertEquals(2, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SAMPLE5' and EMPLOYMENT_STATUS = 'PROBATION'"));
        assertTrue(count("select count(distinct EMPLOYMENT_TYPE) from HRMS.EMPLOYEES where CREATED_BY = 'SAMPLE5'") >= 3);
        assertEquals(2, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SAMPLE5' and FOURTH_NAME is not null"));
        assertEquals(2, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SAMPLE5' and NOTES is not null"));

        // Arabic text round-trips
        Map<String, Object> amira = jdbc.queryForMap(
            "select FIRST_NAME_AR, LAST_NAME_AR, PLACE_OF_BIRTH from HRMS.EMPLOYEES where WORK_EMAIL = 'amira.khalil@nilepet.com'");
        assertEquals("أميرة", amira.get("FIRST_NAME_AR"));
        assertEquals("خليل", amira.get("LAST_NAME_AR"));
        assertEquals("الخرطوم", amira.get("PLACE_OF_BIRTH"));

        // logins: the username is the part of the work e-mail before @, the documented password works, and it must be changed
        Map<String, Object> login = jdbc.queryForMap(
            "select PASSWORD_HASH, MUST_CHANGE_PASSWORD, ROLE from HRMS.AUTH_USERS where USERNAME = 'deng.garang'");
        assertTrue(new BCryptPasswordEncoder().matches("Welcome@123", (String) login.get("PASSWORD_HASH")));
        assertEquals(1, ((Number) login.get("MUST_CHANGE_PASSWORD")).intValue());
        assertEquals("EMPLOYEE", login.get("ROLE"));
        assertEquals(5, count("""
            select count(*) from HRMS.AUTH_USERS u join HRMS.EMPLOYEES e on e.EMPLOYEE_ID = u.EMPLOYEE_ID
             where u.CREATED_BY = 'SAMPLE5' and u.EMPLOYEE_CODE = e.EMPLOYEE_CODE and u.USERNAME = substr(e.WORK_EMAIL, 1, instr(e.WORK_EMAIL, '@') - 1)
            """));

        // running it again adds nothing
        run(SEED);
        assertEquals(5, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SAMPLE5'"));
        assertEquals(5, count("select count(*) from HRMS.AUTH_USERS where CREATED_BY = 'SAMPLE5'"));

        // the rollback removes exactly the five and their logins
        run(ROLLBACK);
        assertEquals(0, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SAMPLE5'"));
        assertEquals(0, count("select count(*) from HRMS.AUTH_USERS where CREATED_BY = 'SAMPLE5'"));
        assertEquals(employeesBefore, count("select count(*) from HRMS.EMPLOYEES"));
    }
}
