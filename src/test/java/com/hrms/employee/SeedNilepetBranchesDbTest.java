package com.hrms.employee;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * db/seed_nilepet_south_sudan_branches.sql runs on a real Oracle schema built from db/migration, loads what it says
 * (9 locations, 10 branches, Arabic intact), does nothing the second time, and its rollback removes it all again.
 * The scripts commit by themselves, so this class is not @Transactional and cleans up what it created.
 */
class SeedNilepetBranchesDbTest extends DatabaseTest {

    @Autowired JdbcTemplate jdbc;

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    /** The anonymous PL/SQL block of the script: from DECLARE to the line that holds only "/". */
    private String block(String file) throws Exception {
        String text = Files.readString(Path.of("db", file));
        int start = text.indexOf("DECLARE");
        int end = text.indexOf("\n/\n", start);
        return text.substring(start, end).trim();
    }

    private String statements(String file) throws Exception {
        return Files.readString(Path.of("db", file));
    }

    @Test
    void seedLoadsOnceIsRepeatableAndRollsBack() throws Exception {
        jdbc.execute("""
            begin
              insert into HRMS.COMPANIES (COMPANY_ID, COMPANIES_CODE, COMPANIES_NAME, COMPANIES_NAME_AR)
              values (HRMS.SEQ_COMPANIES.NEXTVAL, 'NILPET1', 'NILE PETROLEUM', N'x');
              commit;
            end;""");
        String seed = block("seed_nilepet_south_sudan_branches.sql");
        try {
            jdbc.execute(seed);
            assertEquals(9, count("select count(*) from HRMS.OFFICE_LOCATIONS where COUNTRY = 'South Sudan'"));
            assertEquals(10, count("select count(*) from HRMS.COMPANY_BRANCHES"));
            assertEquals(2, count("""
                select count(*) from HRMS.COMPANY_BRANCHES b join HRMS.OFFICE_LOCATIONS l on l.LOCATION_ID = b.LOCATION_ID
                where l.LOCATION_CODE = 'JUBA_HQ'"""));
            assertEquals(0, count("select count(*) from HRMS.COMPANY_BRANCHES where LOCATION_ID is null"));
            assertEquals("جوبا", jdbc.queryForObject(
                "select COMPANY_BRANCHES_NAME_AR from HRMS.COMPANY_BRANCHES where COMPANY_BRANCHES_CODE = 'JUBA_RETAIL'",
                String.class).replace("محطات التجزئة - ", ""));

            jdbc.execute(seed);   // second run: everything exists, nothing changes
            assertEquals(9, count("select count(*) from HRMS.OFFICE_LOCATIONS where COUNTRY = 'South Sudan'"));
            assertEquals(10, count("select count(*) from HRMS.COMPANY_BRANCHES"));

            // The statements run in one block (one session, as in a SQL tool); the pool here does not auto-commit.
            String rollback = statements("seed_nilepet_south_sudan_branches_rollback.sql").replaceAll("(?m)^--.*$", "").trim();
            jdbc.execute("begin " + rollback + " end;");
            assertEquals(0, count("select count(*) from HRMS.OFFICE_LOCATIONS where COUNTRY = 'South Sudan'"));
            assertEquals(0, count("select count(*) from HRMS.COMPANY_BRANCHES"));
        } finally {
            jdbc.execute("""
                begin
                  delete from HRMS.COMPANY_BRANCHES;
                  delete from HRMS.OFFICE_LOCATIONS where COUNTRY = 'South Sudan';
                  delete from HRMS.COMPANIES where COMPANIES_CODE = 'NILPET1';
                  commit;
                end;""");
        }
    }
}
