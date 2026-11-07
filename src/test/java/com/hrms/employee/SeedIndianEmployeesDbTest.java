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
 * db/seed_indian_employees.sql on a real Oracle schema built from db/migration, after the branch seed: 22 employees (20 in
 * two departments, a General Manager and a CEO) with job rows and logins, correct reporting lines and roles, a second run
 * changes nothing, and the rollback removes everything it created. The scripts commit by themselves, so this class is not
 * @Transactional and cleans up after itself.
 */
class SeedIndianEmployeesDbTest extends DatabaseTest {

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

    @Test
    void seedLoadsOnceIsRepeatableAndRollsBack() throws Exception {
        // What the seed expects to exist already: company, the EMPLOYEE role (GENERAL_MANAGER and CEO come from the migrations), shifts, the branch seed, the three departments, existing designations.
        jdbc.execute("""
            begin
              insert into HRMS.COMPANIES (COMPANY_ID, COMPANIES_CODE, COMPANIES_NAME, COMPANIES_NAME_AR)
              values (HRMS.SEQ_COMPANIES.NEXTVAL, 'NILPET1', 'NILE PETROLEUM', N'x');
              for r in (select 'EMPLOYEE' n from dual) loop
                insert into HRMS.ROLES (ROLE_NAME) values (r.n);
              end loop;
              for s in 1..5 loop
                insert into HRMS.WORK_SHIFTS (SHIFT_ID, SHIFT_CODE, SHIFT_NAME, SHIFT_NAME_AR, START_TIME, END_TIME, WORKING_HOURS, WORKING_DAYS)
                values (s, 'SEEDT' || s, 'Seed test shift ' || s, N'x', '08:00', '16:00', 8, 'MON,TUE,WED,THU,FRI');
              end loop;
              for d in (select 'DEP-044' c from dual union all select 'DEP-049' from dual union all select 'DEP-061' from dual) loop
                insert into HRMS.DEPARTMENTS (DEPT_ID, CODE, NAME, NAME_AR) values (HRMS.SEQ_DEPARTMENTS.NEXTVAL, d.c, d.c, N'x');
              end loop;
              for g in (select 'CEO' c, 'DEP-061' d from dual union all select 'SCM_MGR', 'DEP-044' from dual
                        union all select 'PROC_SPEC', 'DEP-044' from dual union all select 'INV_PLAN', 'DEP-044' from dual
                        union all select 'LOG_PLAN', 'DEP-044' from dual) loop
                insert into HRMS.DESIGNATIONS (DESIG_ID, TITLE, CODE, DEPARTMENT_ID)
                values (HRMS.SEQ_DESIGNATIONS.NEXTVAL, g.c, g.c, (select DEPT_ID from HRMS.DEPARTMENTS where CODE = g.d));
              end loop;
              commit;
            end;""");
        String branchSeed = Files.readString(Path.of("db", "seed_nilepet_south_sudan_branches.sql"));
        int start = branchSeed.indexOf("DECLARE");
        jdbc.execute(branchSeed.substring(start, branchSeed.indexOf("\n/\n", start)).trim());
        String seed = "db/seed_indian_employees.sql";
        try {
            runScript(seed);
            assertEquals(22, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SEEDIN'"));
            assertEquals(10, count("select count(*) from HRMS.EMPLOYEES e join HRMS.DEPARTMENTS d on d.DEPT_ID = e.DEPARTMENT_ID where e.CREATED_BY = 'SEEDIN' and d.CODE = 'DEP-049'"));
            assertEquals(10, count("select count(*) from HRMS.EMPLOYEES e join HRMS.DEPARTMENTS d on d.DEPT_ID = e.DEPARTMENT_ID where e.CREATED_BY = 'SEEDIN' and d.CODE = 'DEP-044'"));
            assertEquals(22, count("select count(*) from HRMS.EMPLOYEE_JOB_DETAILS where CREATED_BY = 'SEEDIN' and IS_CURRENT = 1"));
            assertEquals(22, count("select count(*) from HRMS.AUTH_USERS where CREATED_BY = 'SEEDIN' and MUST_CHANGE_PASSWORD = 1 and PASSWORD_HASH like '$2a$10$%' and length(PASSWORD_HASH) = 60"));
            assertEquals(1, count("select count(*) from HRMS.AUTH_USERS where ROLE = 'GENERAL_MANAGER' and USERNAME = 'anil.mehta'"));
            assertEquals(1, count("select count(*) from HRMS.AUTH_USERS where ROLE = 'CEO' and USERNAME = 'vikram.rathore'"));
            assertEquals(20, count("select count(*) from HRMS.AUTH_USERS where ROLE = 'EMPLOYEE' and CREATED_BY = 'SEEDIN'"));
            assertEquals(22, count("select count(distinct WORK_EMAIL) from HRMS.EMPLOYEES where CREATED_BY = 'SEEDIN'"));
            assertEquals(3, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SEEDIN' and EMPLOYMENT_STATUS = 'PROBATION'"));
            // reporting lines: CEO has none, GM reports to CEO, the two heads to the GM, everyone else to their head
            assertEquals(1, count("select count(*) from HRMS.EMPLOYEE_JOB_DETAILS where CREATED_BY = 'SEEDIN' and REPORTING_MANAGER_ID is null"));
            assertEquals(1, count("""
                select count(*) from HRMS.EMPLOYEE_JOB_DETAILS j join HRMS.EMPLOYEES m on m.EMPLOYEE_ID = j.REPORTING_MANAGER_ID
                where m.WORK_EMAIL = 'vikram.rathore@nilepet.com'"""));
            assertEquals(2, count("""
                select count(*) from HRMS.EMPLOYEE_JOB_DETAILS j join HRMS.EMPLOYEES m on m.EMPLOYEE_ID = j.REPORTING_MANAGER_ID
                where m.WORK_EMAIL = 'anil.mehta@nilepet.com'"""));
            assertEquals(0, count("select count(*) from HRMS.EMPLOYEE_JOB_DETAILS where REPORTING_MANAGER_ID = EMPLOYEE_ID"));
            assertEquals(2, count("select count(*) from HRMS.DEPARTMENTS where HEAD_EMPLOYEE_ID is not null"));

            runScript(seed);   // second run: every row exists, nothing changes
            assertEquals(22, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SEEDIN'"));
            assertEquals(22, count("select count(*) from HRMS.EMPLOYEE_JOB_DETAILS where CREATED_BY = 'SEEDIN'"));
            assertEquals(22, count("select count(*) from HRMS.AUTH_USERS where CREATED_BY = 'SEEDIN'"));
            assertEquals(9, count("select count(*) from HRMS.DESIGNATIONS where CODE in ('GM','BAPP_MGR','BAPP_SR_ENG','BAPP_ENG','BAPP_BA','BAPP_DBA','BAPP_QA','BAPP_SUP','PROC_OFFICER')"));

            runScript("db/seed_indian_employees_rollback.sql");
            assertEquals(0, count("select count(*) from HRMS.EMPLOYEES where CREATED_BY = 'SEEDIN'"));
            assertEquals(0, count("select count(*) from HRMS.AUTH_USERS where CREATED_BY = 'SEEDIN'"));
            assertEquals(0, count("select count(*) from HRMS.EMPLOYEE_JOB_DETAILS where CREATED_BY = 'SEEDIN'"));
            assertEquals(0, count("select count(*) from HRMS.DESIGNATIONS where CODE in ('GM','BAPP_MGR','BAPP_SR_ENG','BAPP_ENG','BAPP_BA','BAPP_DBA','BAPP_QA','BAPP_SUP','PROC_OFFICER')"));
            assertEquals(0, count("select count(*) from HRMS.DEPARTMENTS where HEAD_EMPLOYEE_ID is not null"));
        } finally {
            jdbc.execute("""
                begin
                  update HRMS.DEPARTMENTS set HEAD_EMPLOYEE_ID = null;
                  delete from HRMS.AUTH_USERS where CREATED_BY = 'SEEDIN';
                  delete from HRMS.EMPLOYEE_JOB_DETAILS where CREATED_BY = 'SEEDIN';
                  delete from HRMS.EMPLOYEES where CREATED_BY = 'SEEDIN';
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
