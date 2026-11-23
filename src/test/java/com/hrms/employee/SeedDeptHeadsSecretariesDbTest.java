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

/**
 * db/seed_dept_heads_secretaries.sql on a real Oracle schema with four of its departments: an empty one (DEP-052) gets head,
 * secretary and 3 staff; one with a head (DEP-006) keeps that head and gets a secretary and 3 staff; one with an existing
 * "Public Relations Officer" title (DEP-055) reuses it; one with both posts filled (DEP-039) is left alone. Departments that do
 * not exist are skipped. A second run changes nothing; the rollback removes only what the seed created. The scripts commit by
 * themselves, so this class is not @Transactional and cleans up after itself.
 */
class SeedDeptHeadsSecretariesDbTest extends DatabaseTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private static List<String> statements(String text) {
        return Arrays.stream(text.replaceAll("(?m)^--.*$", "").split(";\\s*\\n"))
            .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private void runScript(String file) throws Exception {
        List<String> all = statements(Files.readString(Path.of(file)));
        new TransactionTemplate(txManager).executeWithoutResult(s -> all.forEach(jdbc::execute));
    }

    private long seeded(String deptCode) {
        return count("select count(*) from HRMS.EMPLOYEES e join HRMS.DEPARTMENTS d on d.DEPT_ID = e.DEPARTMENT_ID"
            + " where e.CREATED_BY = 'SEEDDH' and d.CODE = '" + deptCode + "'");
    }

    @Test
    void fillsEmptySlotsOnlyIsRepeatableAndRollsBack() throws Exception {
        jdbc.execute("""
            begin
              insert into HRMS.COMPANIES (COMPANY_ID, COMPANIES_CODE, COMPANIES_NAME, COMPANIES_NAME_AR)
              values (HRMS.SEQ_COMPANIES.NEXTVAL, 'NILPET1', 'NILE PETROLEUM', N'x');
              insert into HRMS.ROLES (ROLE_NAME) values ('EMPLOYEE');
              for s in 1..5 loop
                insert into HRMS.WORK_SHIFTS (SHIFT_ID, SHIFT_CODE, SHIFT_NAME, SHIFT_NAME_AR, START_TIME, END_TIME, WORKING_HOURS, WORKING_DAYS)
                values (s, 'SEEDT' || s, 'Seed test shift ' || s, N'x', '08:00', '16:00', 8, 'MON,TUE,WED,THU,FRI');
              end loop;
              for d in (select 'DEP-052' c, 'Financial Audit Department' n from dual
                        union all select 'DEP-006', 'Technical Services Department' from dual
                        union all select 'DEP-055', 'Public Relations Department' from dual
                        union all select 'DEP-039', 'Accounting Department' from dual) loop
                insert into HRMS.DEPARTMENTS (DEPT_ID, CODE, NAME, NAME_AR) values (HRMS.SEQ_DEPARTMENTS.NEXTVAL, d.c, d.n, N'قسم');
              end loop;
              insert into HRMS.DESIGNATIONS (DESIG_ID, TITLE, CODE, DEPARTMENT_ID)
              values (HRMS.SEQ_DESIGNATIONS.NEXTVAL, 'Public Relations Officer', 'PR_OFF',
                      (select DEPT_ID from HRMS.DEPARTMENTS where CODE = 'DEP-055'));
              insert into HRMS.EMPLOYEES (EMPLOYEE_ID, EMPLOYEE_CODE, FIRST_NAME, FIRST_NAME_AR, LAST_NAME, LAST_NAME_AR,
                  DATE_OF_BIRTH, GENDER, PERSONAL_EMAIL, WORK_EMAIL, HIRE_DATE, EMPLOYMENT_TYPE)
              values (HRMS.SEQ_EMPLOYEES.NEXTVAL, 'PRE-HEAD-1', 'Pre', N'x', 'Head', N'x', DATE '1980-01-01', 'MALE',
                      'pre.head@example.test', 'pre.head@nilepet.test', DATE '2015-01-01', 'FULL_TIME');
              update HRMS.DEPARTMENTS set HEAD_EMPLOYEE_ID = (select EMPLOYEE_ID from HRMS.EMPLOYEES where EMPLOYEE_CODE = 'PRE-HEAD-1')
               where CODE in ('DEP-006', 'DEP-039');
              update HRMS.DEPARTMENTS set SECRETARY_EMPLOYEE_ID = (select EMPLOYEE_ID from HRMS.EMPLOYEES where EMPLOYEE_CODE = 'PRE-HEAD-1')
               where CODE = 'DEP-039';
              commit;
            end;""");
        String branchSeed = Files.readString(Path.of("db", "seed_nilepet_south_sudan_branches.sql"));
        int start = branchSeed.indexOf("DECLARE");
        jdbc.execute(branchSeed.substring(start, branchSeed.indexOf("\n/\n", start)).trim());
        String seed = "db/seed_dept_heads_secretaries.sql";
        String preHead = "(select EMPLOYEE_ID from HRMS.EMPLOYEES where EMPLOYEE_CODE = 'PRE-HEAD-1')";
        try {
            runScript(seed);
            assertEquals(14, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SEEDDH'"));
            assertEquals(5, seeded("DEP-052"));
            assertEquals(4, seeded("DEP-006"));
            assertEquals(5, seeded("DEP-055"));
            assertEquals(0, seeded("DEP-039"));
            // slots: new head where empty, existing head kept; a secretary everywhere it was empty
            assertEquals(2, count("""
                select count(*) from HRMS.DEPARTMENTS d join HRMS.EMPLOYEES e on e.EMPLOYEE_ID = d.HEAD_EMPLOYEE_ID
                where e.CREATED_BY = 'SEEDDH' and d.CODE in ('DEP-052', 'DEP-055')"""));
            assertEquals(2, count("select count(*) from HRMS.DEPARTMENTS where HEAD_EMPLOYEE_ID = " + preHead));
            assertEquals(3, count("""
                select count(*) from HRMS.DEPARTMENTS d join HRMS.EMPLOYEES e on e.EMPLOYEE_ID = d.SECRETARY_EMPLOYEE_ID
                where e.CREATED_BY = 'SEEDDH'"""));
            assertEquals(1, count("select count(*) from HRMS.DEPARTMENTS where CODE = 'DEP-039' and SECRETARY_EMPLOYEE_ID = " + preHead));
            // designations: 3 + 2 + 2 new; the existing PR Officer is reused, not duplicated
            assertEquals(7, count("select count(*) from HRMS.DESIGNATIONS where CODE like 'SDH-%'"));
            assertEquals(3, count("""
                select count(*) from HRMS.EMPLOYEES e join HRMS.DESIGNATIONS g on g.DESIG_ID = e.DESIGNATION_ID
                where e.CREATED_BY = 'SEEDDH' and g.CODE = 'PR_OFF'"""));
            // job rows, reporting lines (no GM here: new heads report to nobody; everyone else to their head), logins
            assertEquals(14, count("select count(*) from HRMS.EMPLOYEE_JOB_DETAILS where CREATED_BY = 'SEEDDH' and IS_CURRENT = 1 and DESIGNATION_ID is not null"));
            assertEquals(2, count("select count(*) from HRMS.EMPLOYEE_JOB_DETAILS where CREATED_BY = 'SEEDDH' and REPORTING_MANAGER_ID is null"));
            assertEquals(12, count("""
                select count(*) from HRMS.EMPLOYEE_JOB_DETAILS j join HRMS.DEPARTMENTS d on d.DEPT_ID = j.DEPARTMENT_ID
                where j.CREATED_BY = 'SEEDDH' and j.REPORTING_MANAGER_ID = d.HEAD_EMPLOYEE_ID"""));
            assertEquals(4, count("select count(*) from HRMS.EMPLOYEE_JOB_DETAILS where CREATED_BY = 'SEEDDH' and REPORTING_MANAGER_ID = " + preHead));
            assertEquals(14, count("select count(*) from HRMS.AUTH_USERS where CREATED_BY = 'SEEDDH' and ROLE = 'EMPLOYEE' and MUST_CHANGE_PASSWORD = 0 and length(PASSWORD_HASH) = 60"));

            runScript(seed);   // second run: nothing new
            assertEquals(14, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SEEDDH'"));
            assertEquals(14, count("select count(*) from HRMS.EMPLOYEE_JOB_DETAILS where CREATED_BY = 'SEEDDH'"));
            assertEquals(14, count("select count(*) from HRMS.AUTH_USERS where CREATED_BY = 'SEEDDH'"));
            assertEquals(7, count("select count(*) from HRMS.DESIGNATIONS where CODE like 'SDH-%'"));

            runScript("db/seed_dept_heads_secretaries_rollback.sql");
            assertEquals(0, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SEEDDH'"));
            assertEquals(0, count("select count(*) from HRMS.AUTH_USERS where CREATED_BY = 'SEEDDH'"));
            assertEquals(0, count("select count(*) from HRMS.EMPLOYEE_JOB_DETAILS where CREATED_BY = 'SEEDDH'"));
            assertEquals(0, count("select count(*) from HRMS.DESIGNATIONS where CODE like 'SDH-%'"));
            assertEquals(1, count("select count(*) from HRMS.DESIGNATIONS where CODE = 'PR_OFF'"));
            assertEquals(2, count("select count(*) from HRMS.DEPARTMENTS where HEAD_EMPLOYEE_ID is not null"));
            assertEquals(1, count("select count(*) from HRMS.DEPARTMENTS where SECRETARY_EMPLOYEE_ID is not null"));
        } finally {
            jdbc.execute("""
                begin
                  update HRMS.DEPARTMENTS set HEAD_EMPLOYEE_ID = null, SECRETARY_EMPLOYEE_ID = null;
                  delete from HRMS.AUTH_USERS where CREATED_BY = 'SEEDDH';
                  delete from HRMS.EMPLOYEE_JOB_DETAILS where CREATED_BY = 'SEEDDH';
                  delete from HRMS.EMPLOYEES where CREATED_BY = 'SEEDDH' or EMPLOYEE_CODE = 'PRE-HEAD-1';
                  delete from HRMS.DESIGNATIONS;
                  delete from HRMS.DEPARTMENTS;
                  delete from HRMS.COMPANY_BRANCHES;
                  delete from HRMS.OFFICE_LOCATIONS where COUNTRY = 'South Sudan';
                  delete from HRMS.WORK_SHIFTS where SHIFT_CODE like 'SEEDT%';
                  delete from HRMS.ROLES where ROLE_NAME = 'EMPLOYEE';
                  delete from HRMS.COMPANIES where COMPANIES_CODE = 'NILPET1';
                  commit;
                end;""");
        }
    }
}
