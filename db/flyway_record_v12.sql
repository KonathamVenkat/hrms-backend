-- Record V12__grade_levels.sql in Flyway's history, AFTER you have run that script by hand.
-- The app only validates: with V12 in db/migration but not recorded it refuses to start.
-- If the file is edited later, the app prints "-> Resolved locally : <number>" at startup; UPDATE this row's "checksum".
INSERT INTO HRMS."flyway_schema_history"
    ("installed_rank", "version", "description", "type", "script", "checksum", "installed_by", "execution_time", "success")
SELECT MAX("installed_rank") + 1, '12', 'grade levels', 'SQL', 'V12__grade_levels.sql', 18137182, 'HRMS', 0, 1
  FROM HRMS."flyway_schema_history";
COMMIT;
