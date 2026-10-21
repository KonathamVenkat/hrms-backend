package com.hrms.employee;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * db/seed_grades_uhrpm.sql runs on a real Oracle schema built from db/migration, loads grades 1 to 24 in order with
 * Arabic intact, does nothing the second time, and its rollback removes them again. The scripts commit by
 * themselves, so this class is not @Transactional and cleans up what it created.
 */
class SeedGradesDbTest extends DatabaseTest {

    @Autowired JdbcTemplate jdbc;

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    @Test
    void seedLoadsOnceIsRepeatableAndRollsBack() throws Exception {
        String text = Files.readString(Path.of("db", "seed_grades_uhrpm.sql"));
        int start = text.indexOf("DECLARE");
        String seed = text.substring(start, text.indexOf("\n/\n", start)).trim();
        try {
            jdbc.execute(seed);
            assertEquals(24, count("select count(*) from HRMS.GRADES"));
            assertEquals(24, count("select count(distinct SORT_ORDER) from HRMS.GRADES where SORT_ORDER between 1 and 24"));
            assertEquals("G07", jdbc.queryForObject(
                "select GRADES_CODE from HRMS.GRADES where SORT_ORDER = 7", String.class));
            assertEquals("Grade 24", jdbc.queryForObject(
                "select GRADES_NAME from HRMS.GRADES where GRADES_CODE = 'G24'", String.class));
            assertEquals("الدرجة 10", jdbc.queryForObject(
                "select GRADES_NAME_AR from HRMS.GRADES where GRADES_CODE = 'G10'", String.class));
            assertEquals(0, count("select count(*) from HRMS.GRADES where COMPANY_ID is not null or IS_ACTIVE <> 1"));

            jdbc.execute(seed);   // second run: everything exists, nothing changes
            assertEquals(24, count("select count(*) from HRMS.GRADES"));

            // The statements run in one block (one session, as in a SQL tool); the pool here does not auto-commit.
            String rollback = Files.readString(Path.of("db", "seed_grades_uhrpm_rollback.sql"))
                .replaceAll("(?m)^--.*$", "").trim();
            jdbc.execute("begin " + rollback + " end;");
            assertEquals(0, count("select count(*) from HRMS.GRADES"));
        } finally {
            jdbc.execute("begin delete from HRMS.GRADES; commit; end;");
        }
    }
}
