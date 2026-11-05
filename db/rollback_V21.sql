-- ROLLBACK of V21__job_details_version.sql. Stop the backend first. Run as the HRMS user, by hand. Then:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '21';  COMMIT;
-- and remove the migration file, or the app will not start. The previous backend build runs without the column.
ALTER TABLE HRMS.EMPLOYEE_JOB_DETAILS DROP (VERSION);
