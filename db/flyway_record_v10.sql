-- Record V10__approval_workflow.sql in Flyway's history, AFTER you have run that script by hand.
-- The app only validates: with V10 in db/migration but not recorded it refuses to start
-- ("Detected resolved migration not applied to database: 10").
-- The checksum is the one Flyway computes for the file as committed. If the file is edited later, the app
-- prints "-> Resolved locally : <number>" at startup; UPDATE this row's "checksum" to that number.
INSERT INTO HRMS."flyway_schema_history"
    ("installed_rank", "version", "description", "type", "script", "checksum", "installed_by", "execution_time", "success")
SELECT MAX("installed_rank") + 1, '10', 'approval workflow', 'SQL', 'V10__approval_workflow.sql', -1151467567, 'HRMS', 0, 1
  FROM HRMS."flyway_schema_history";
COMMIT;
