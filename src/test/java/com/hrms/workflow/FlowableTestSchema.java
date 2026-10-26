package com.hrms.workflow;

import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.oracle.OracleContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Installs the FLOWABLE schema into the throwaway Oracle container by running the same hand-run script that
 * production uses (db/flowable_schema_setup.sql). Idempotent, so every DB test context can call it.
 */
public final class FlowableTestSchema {

    private static final String SCRIPT = "db/flowable_schema_setup.sql";

    private FlowableTestSchema() {
    }

    public static synchronized void install(OracleContainer oracle) {
        // SYSTEM shares the container password; the HRMS app user may not create users.
        try (Connection c = DriverManager.getConnection(oracle.getJdbcUrl(), "system", oracle.getPassword())) {
            if (exists(c)) {
                return;
            }
            ScriptUtils.executeSqlScript(c, new FileSystemResource(locateScript()));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not install the FLOWABLE test schema", e);
        }
    }

    /** Walks up from the working directory so the test also works when it is not started from the project root. */
    private static Path locateScript() {
        for (Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(SCRIPT);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(SCRIPT + " not found above " + System.getProperty("user.dir"));
    }

    private static boolean exists(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("select count(*) from ALL_USERS where USERNAME = 'FLOWABLE'")) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }
}
