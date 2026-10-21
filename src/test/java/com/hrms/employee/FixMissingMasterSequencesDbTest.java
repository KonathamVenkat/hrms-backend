package com.hrms.employee;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * db/fix_missing_master_sequences.sql puts back a master sequence that is missing (as found in the dev DB) and
 * leaves the others alone, also when run twice. Not @Transactional: DDL commits by itself.
 */
class FixMissingMasterSequencesDbTest extends DatabaseTest {

    @Autowired JdbcTemplate jdbc;

    private long sequences() {
        Long n = jdbc.queryForObject("""
            select count(*) from ALL_SEQUENCES where SEQUENCE_OWNER = 'HRMS'
               and SEQUENCE_NAME in ('SEQ_RELIGIONS', 'SEQ_RELATION_TYPES', 'SEQ_REGIONS', 'SEQ_GRADES')""", Long.class);
        return n == null ? 0 : n;
    }

    @Test
    void createsWhatIsMissingAndKeepsWhatExists() throws Exception {
        String text = Files.readString(Path.of("db", "fix_missing_master_sequences.sql"));
        int start = text.indexOf("DECLARE");
        String fix = text.substring(start, text.indexOf("\nEND;\n/", start) + "\nEND;".length()).trim();

        assertEquals(4, sequences());   // the schema from db/migration has them all
        jdbc.execute("drop sequence HRMS.SEQ_RELIGIONS");
        jdbc.execute("drop sequence HRMS.SEQ_RELATION_TYPES");
        assertEquals(2, sequences());

        jdbc.execute(fix);
        assertEquals(4, sequences());

        jdbc.execute(fix);   // second run changes nothing
        assertEquals(4, sequences());
    }
}
