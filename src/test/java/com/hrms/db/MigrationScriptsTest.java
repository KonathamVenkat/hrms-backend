package com.hrms.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migrations are run by hand (Flyway only validates). A script that changes data (INSERT, UPDATE, DELETE, MERGE)
 * must end its work with COMMIT: the DDL in the same file commits by itself, the data changes after it do not, and a
 * session that closes without COMMIT silently loses them while the history row says the migration is applied.
 */
class MigrationScriptsTest {

    private static final Pattern DML = Pattern.compile("(?im)^[ \\t]*(INSERT|UPDATE|DELETE|MERGE)\\b");
    private static final Pattern COMMIT = Pattern.compile("(?im)^[ \\t]*COMMIT[ \\t]*;");

    @Test
    void theCheckRecognisesDataChangesAndCommit() {
        assertTrue(DML.matcher("ALTER TABLE T ADD (C NUMBER);\nUPDATE T SET C = 1;\n").find());
        assertFalse(DML.matcher("-- UPDATE in a comment\nALTER TABLE T ADD (C NUMBER);\n").find());
        assertTrue(COMMIT.matcher("UPDATE T SET C = 1;\nCOMMIT;\n").find());
        assertFalse(COMMIT.matcher("UPDATE T SET C = 1;\n").find());
    }

    @Test
    void everyMigrationThatChangesDataCommits() throws IOException {
        List<Path> scripts;
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
            scripts = files.filter(p -> p.getFileName().toString().endsWith(".sql")).toList();
        }
        assertFalse(scripts.isEmpty(), "no migration scripts found");
        for (Path script : scripts) {
            String sql = Files.readString(script);
            if (DML.matcher(sql).find()) {
                assertTrue(COMMIT.matcher(sql).find(), script.getFileName() + " changes data but never COMMITs");
            }
        }
    }
}
