-- Record V19__bank_branch_contact.sql in Flyway's history, AFTER you have run that script by hand.
-- The app only validates: with V19 in db/migration but not recorded it refuses to start.
-- Safe to run twice: the row is added only when this version is not recorded yet.
-- If the file is edited later, the app prints "-> Resolved locally : <number>" at startup; UPDATE this row's "checksum".
INSERT INTO HRMS."flyway_schema_history"
    ("installed_rank", "version", "description", "type", "script", "checksum", "installed_by", "execution_time", "success")
SELECT h.next_rank, '19', 'bank branch contact', 'SQL', 'V19__bank_branch_contact.sql', 131768149, 'HRMS', 0, 1
  FROM (SELECT MAX("installed_rank") + 1 AS next_rank FROM HRMS."flyway_schema_history") h
 WHERE NOT EXISTS (SELECT 1 FROM HRMS."flyway_schema_history" WHERE "version" = '19');
COMMIT;
