-- Record V7__recruitment_requisitions.sql in Flyway's history, AFTER you have run that script by hand.
-- The app only validates: with V7 in db/migration but not recorded it refuses to start
-- ("Detected resolved migration not applied to database: 7").
-- The checksum is the one Flyway computes for the file as committed. If the file is edited later, the app
-- prints "-> Resolved locally : <number>" at startup; UPDATE this row's "checksum" to that number.
INSERT INTO HRMS."flyway_schema_history"
    ("installed_rank", "version", "description", "type", "script", "checksum", "installed_by", "execution_time", "success")
VALUES (6, '7', 'recruitment requisitions', 'SQL', 'V7__recruitment_requisitions.sql', -1695408714, 'HRMS', 0, 1);
COMMIT;
