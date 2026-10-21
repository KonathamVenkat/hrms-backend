package com.hrms.employee;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * db/seed_masters_reference.sql runs on a real Oracle schema built from db/migration, loads the reference lists (the
 * geography chain and the relations under their parents, Arabic intact), does nothing the second time, and its
 * rollback removes them all again. The scripts commit by themselves, so this class is not @Transactional and cleans
 * up what it created.
 */
class SeedMastersDbTest extends DatabaseTest {

    @Autowired JdbcTemplate jdbc;

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    /** The script's statements as one block (one session, as in a SQL tool); the pool here does not auto-commit. */
    private String block(String file) throws Exception {
        String sql = Files.readString(Path.of("db", file)).replaceAll("(?m)--.*$", "").trim();
        return "begin " + sql + " end;";
    }

    private long total() {
        return count("""
            select (select count(*) from HRMS.REGIONS) + (select count(*) from HRMS.WILAYAS)
                 + (select count(*) from HRMS.PROVINCES) + (select count(*) from HRMS.CITIES)
                 + (select count(*) from HRMS.RELIGIONS) + (select count(*) from HRMS.NATIONALITIES)
                 + (select count(*) from HRMS.CONTRACT_TYPES) + (select count(*) from HRMS.PROFESSIONS)
                 + (select count(*) from HRMS.EDUCATION_LEVELS) + (select count(*) from HRMS.LANGUAGES)
                 + (select count(*) from HRMS.LANGUAGE_LEVELS) + (select count(*) from HRMS.HOUSE_TYPES)
                 + (select count(*) from HRMS.RELATION_TYPES) + (select count(*) from HRMS.RELATIONS)
            from DUAL""");
    }

    @Test
    void seedLoadsOnceIsRepeatableAndRollsBack() throws Exception {
        String seed = block("seed_masters_reference.sql");
        try {
            jdbc.execute(seed);
            assertEquals(3, count("select count(*) from HRMS.REGIONS"));
            assertEquals(13, count("select count(*) from HRMS.WILAYAS"));
            assertEquals(8, count("select count(*) from HRMS.CITIES"));
            assertEquals(12, count("select count(*) from HRMS.RELATIONS"));
            long loaded = total();

            // every child sits under a parent that exists, down the whole chain
            assertEquals(8, count("""
                select count(*) from HRMS.CITIES c join HRMS.PROVINCES p on p.PROVINCE_ID = c.PROVINCE_ID
                  join HRMS.WILAYAS w on w.WILAYA_ID = p.WILAYA_ID join HRMS.REGIONS r on r.REGION_ID = w.REGION_ID"""));
            assertEquals(12, count("""
                select count(*) from HRMS.RELATIONS r join HRMS.RELATION_TYPES t on t.RELATION_TYPE_ID = r.RELATION_TYPE_ID"""));
            assertEquals("جوبا", jdbc.queryForObject(
                "select CITIES_NAME_AR from HRMS.CITIES where CITIES_CODE = 'JUBA'", String.class));
            assertEquals(0, count("select count(*) from HRMS.NATIONALITIES where IS_ACTIVE <> 1"));

            jdbc.execute(seed);   // second run: everything exists, nothing changes
            assertEquals(loaded, total());

            jdbc.execute(block("seed_masters_reference_rollback.sql"));
            assertEquals(0, total());
        } finally {
            jdbc.execute("""
                begin
                  delete from HRMS.RELATIONS; delete from HRMS.RELATION_TYPES; delete from HRMS.HOUSE_TYPES;
                  delete from HRMS.LANGUAGE_LEVELS; delete from HRMS.LANGUAGES; delete from HRMS.EDUCATION_LEVELS;
                  delete from HRMS.PROFESSIONS; delete from HRMS.CONTRACT_TYPES; delete from HRMS.NATIONALITIES;
                  delete from HRMS.RELIGIONS; delete from HRMS.CITIES; delete from HRMS.PROVINCES;
                  delete from HRMS.WILAYAS; delete from HRMS.REGIONS;
                  commit;
                end;""");
        }
    }
}
