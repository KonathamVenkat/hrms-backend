-- One-off repair for the dev database (2026-11-22): Flyway's history has a FAILED row for V29 (success = 0) with the
-- checksum of an early draft of V29, so the app stops with "Detected failed migration to version 29".
-- Checked read-only before writing this: INCREMENT_NO NUMBER(2,0) and CK_EJD_GRADE_INCREMENT (the final text) are in
-- place, so the current V29 IS applied; only the history row is wrong. This marks it successful with the checksum of
-- the current file. Run as HRMS with the backend stopped. Does nothing when the row is already fine.
-- Do NOT run it on a database where INCREMENT_NO is missing: there, run V29 itself first.
UPDATE HRMS."flyway_schema_history"
   SET "success" = 1,
       "checksum" = 609354922
 WHERE "version" = '29'
   AND ("success" = 0 OR "checksum" <> 609354922)
   AND EXISTS (SELECT 1 FROM user_tab_columns
                WHERE table_name = 'EMPLOYEE_JOB_DETAILS' AND column_name = 'INCREMENT_NO');
COMMIT;
