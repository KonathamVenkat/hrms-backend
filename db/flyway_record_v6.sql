-- Record V6__char_length_semantics.sql in Flyway's history, AFTER you have run that script by hand.
-- Copy the WHOLE statement, including the final 1 (success). A row with success = 0 is read as a FAILED migration and
-- the app refuses to start ("Detected failed migration to version 6").
-- The checksum is the one Flyway computes for the file as committed; if the file is edited later the app prints
-- "-> Resolved locally : <number>" and you UPDATE this row's "checksum" to that number.
INSERT INTO HRMS."flyway_schema_history"
    ("installed_rank", "version", "description", "type", "script", "checksum", "installed_by", "execution_time", "success")
VALUES (5, '6', 'char length semantics', 'SQL', 'V6__char_length_semantics.sql', 516474583, 'HRMS', 0, 1);
COMMIT;
