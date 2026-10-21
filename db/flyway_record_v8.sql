-- Record V8__organisation_branches.sql in Flyway's history, AFTER you have run that script by hand.
-- The app only validates: with V8 in db/migration but not recorded it refuses to start
-- ("Detected resolved migration not applied to database: 8").
-- The checksum is the one Flyway computes for the file as committed. If the file is edited later, the app
-- prints "-> Resolved locally : <number>" at startup; UPDATE this row's "checksum" to that number.
INSERT INTO HRMS."flyway_schema_history"
    ("installed_rank", "version", "description", "type", "script", "checksum", "installed_by", "execution_time", "success")
VALUES (7, '8', 'organisation branches', 'SQL', 'V8__organisation_branches.sql', 1526495816, 'HRMS', 0, 1);
COMMIT;
