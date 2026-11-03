-- Record V18__drop_identity_document_columns.sql in Flyway's history, AFTER you have run that script by hand.
-- The app only validates: with V18 in db/migration but not recorded it refuses to start.
-- Safe to run twice: the row is added only when this version is not recorded yet.
-- If the file is edited later, the app prints "-> Resolved locally : <number>" at startup; UPDATE this row's "checksum".
INSERT INTO HRMS."flyway_schema_history"
    ("installed_rank", "version", "description", "type", "script", "checksum", "installed_by", "execution_time", "success")
SELECT h.next_rank, '18', 'drop identity document columns', 'SQL', 'V18__drop_identity_document_columns.sql', -1990197807, 'HRMS', 0, 1
  FROM (SELECT MAX("installed_rank") + 1 AS next_rank FROM HRMS."flyway_schema_history") h
 WHERE NOT EXISTS (SELECT 1 FROM HRMS."flyway_schema_history" WHERE "version" = '18');
COMMIT;
