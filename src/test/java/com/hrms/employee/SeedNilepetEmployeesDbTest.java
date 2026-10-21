package com.hrms.employee;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * db/seed_nilepet_departments_employees.sql on a real Oracle schema built from db/migration, after the branch seed:
 * it loads 10 departments, 40 designations and 100 employees with job rows and logins, every branch sits in the
 * employee's own location, a second run changes nothing, and the rollback removes everything it created.
 * The scripts commit by themselves, so this class is not @Transactional and cleans up after itself.
 */
class SeedNilepetEmployeesDbTest extends DatabaseTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private static String stripComments(String text) {
        return text.replaceAll("(?m)^--.*$", "");
    }

    /** The statements of a plain script: comment lines removed, split on the semicolon that ends a line. */
    private static List<String> statements(String text) {
        return Arrays.stream(stripComments(text).split(";\\s*\\n"))
            .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private void runScript(String file) throws Exception {
        List<String> all = statements(Files.readString(Path.of(file)));
        new TransactionTemplate(txManager).executeWithoutResult(s -> all.forEach(jdbc::execute));
    }

    @Test
    void seedLoadsOnceIsRepeatableAndRollsBack() throws Exception {
        // What the seed expects to exist already: company, roles, shifts, the branch seed, the original departments and designations.
        jdbc.execute("""
            begin
              insert into HRMS.COMPANIES (COMPANY_ID, COMPANIES_CODE, COMPANIES_NAME, COMPANIES_NAME_AR)
              values (HRMS.SEQ_COMPANIES.NEXTVAL, 'NILPET1', 'NILE PETROLEUM', N'x');
              for r in (select 'HR_ADMIN' n from dual union all select 'HR_MANAGER' from dual union all select 'EMPLOYEE' from dual) loop
                insert into HRMS.ROLES (ROLE_NAME) values (r.n);
              end loop;
              for s in 1..5 loop
                insert into HRMS.WORK_SHIFTS (SHIFT_ID, SHIFT_CODE, SHIFT_NAME, SHIFT_NAME_AR, START_TIME, END_TIME, WORKING_HOURS, WORKING_DAYS)
                values (s, 'SEEDT' || s, 'Seed test shift ' || s, N'x', '08:00', '16:00', 8, 'MON,TUE,WED,THU,FRI');
              end loop;
              commit;
            end;""");
        String branchSeed = Files.readString(Path.of("db", "seed_nilepet_south_sudan_branches.sql"));
        int start = branchSeed.indexOf("DECLARE");
        jdbc.execute(branchSeed.substring(start, branchSeed.indexOf("\n/\n", start)).trim());
        runScript("src/test/resources/db/seed-employees-prereq.sql");
        String seed = "db/seed_nilepet_departments_employees.sql";
        try {
            runScript(seed);
            assertEquals(10, count("select count(*) from HRMS.DEPARTMENTS where CODE in ('HSE','RETAIL','DEPOT','SCM','ENGMNT','TRADE','AUDIT','COMMS','SEC','FLEET')"));
            assertEquals(40, count("select count(*) from HRMS.DESIGNATIONS where TITLE_AR is not null"));
            assertEquals(100, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SEED'"));
            assertEquals(100, count("select count(*) from HRMS.EMPLOYEE_JOB_DETAILS where CREATED_BY = 'SEED' and IS_CURRENT = 1"));
            assertEquals(100, count("select count(*) from HRMS.AUTH_USERS where CREATED_BY = 'SEED' and MUST_CHANGE_PASSWORD = 1"));
            assertEquals(1, count("select count(*) from HRMS.AUTH_USERS where ROLE = 'HR_ADMIN'"));
            assertEquals(2, count("select count(*) from HRMS.AUTH_USERS where ROLE = 'HR_MANAGER'"));
            assertEquals(97, count("select count(*) from HRMS.AUTH_USERS where ROLE = 'EMPLOYEE'"));
            assertEquals(8, count("select count(*) from HRMS.EMPLOYEE_JOB_DETAILS where CREATED_BY = 'SEED' and COMPANY_BRANCH_ID is null"));
            assertEquals(0, count("""
                select count(*) from HRMS.EMPLOYEE_JOB_DETAILS j join HRMS.COMPANY_BRANCHES b on b.COMPANY_BRANCH_ID = j.COMPANY_BRANCH_ID
                where j.CREATED_BY = 'SEED' and nvl(b.LOCATION_ID, -1) <> j.LOCATION_ID"""));
            assertEquals(1, count("select count(*) from HRMS.EMPLOYEE_JOB_DETAILS where CREATED_BY = 'SEED' and REPORTING_MANAGER_ID is null"));
            assertEquals(0, count("select count(*) from HRMS.EMPLOYEE_JOB_DETAILS where REPORTING_MANAGER_ID = EMPLOYEE_ID"));
            assertEquals(19, count("select count(*) from HRMS.DEPARTMENTS where HEAD_EMPLOYEE_ID is not null"));
            assertEquals(12, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SEED' and EMPLOYMENT_STATUS = 'PROBATION'"));
            assertEquals(100, count("select count(distinct WORK_EMAIL) from HRMS.EMPLOYEES where CREATED_BY = 'SEED'"));
            assertEquals(100, count("select count(*) from HRMS.AUTH_USERS where CREATED_BY = 'SEED' and PASSWORD_HASH like '$2a$10$%' and length(PASSWORD_HASH) = 60"));
            assertEquals("مدير الصحة والسلامة والبيئة",
                jdbc.queryForObject("select TITLE_AR from HRMS.DESIGNATIONS where CODE = 'HSE_MGR'", String.class));

            runScript(seed);   // second run: every row exists, nothing changes
            assertEquals(100, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SEED'"));
            assertEquals(100, count("select count(*) from HRMS.EMPLOYEE_JOB_DETAILS where CREATED_BY = 'SEED'"));
            assertEquals(100, count("select count(*) from HRMS.AUTH_USERS where CREATED_BY = 'SEED'"));
            assertEquals(40, count("select count(*) from HRMS.DESIGNATIONS where TITLE_AR is not null"));

            runScript("db/seed_nilepet_departments_employees_rollback.sql");
            assertEquals(0, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SEED'"));
            assertEquals(0, count("select count(*) from HRMS.AUTH_USERS where CREATED_BY = 'SEED'"));
            assertEquals(0, count("select count(*) from HRMS.DEPARTMENTS where CODE in ('HSE','RETAIL','DEPOT','SCM','ENGMNT','TRADE','AUDIT','COMMS','SEC','FLEET')"));
            assertTrue(count("select count(*) from HRMS.DEPARTMENTS where HEAD_EMPLOYEE_ID is not null") == 0);
        } finally {
            jdbc.execute("""
                begin
                  update HRMS.DEPARTMENTS set HEAD_EMPLOYEE_ID = null;
                  delete from HRMS.AUTH_USERS where CREATED_BY = 'SEED';
                  delete from HRMS.EMPLOYEE_JOB_DETAILS where CREATED_BY = 'SEED';
                  delete from HRMS.EMPLOYEES where CREATED_BY = 'SEED';
                  delete from HRMS.DESIGNATIONS;
                  delete from HRMS.DEPARTMENTS;
                  delete from HRMS.COMPANY_BRANCHES;
                  delete from HRMS.OFFICE_LOCATIONS where COUNTRY = 'South Sudan';
                  delete from HRMS.WORK_SHIFTS where SHIFT_CODE like 'SEEDT%';
                  delete from HRMS.ROLES where ROLE_NAME in ('HR_ADMIN','HR_MANAGER','EMPLOYEE');
                  delete from HRMS.COMPANIES where COMPANIES_CODE = 'NILPET1';
                  commit;
                end;""");
        }
    }
}
