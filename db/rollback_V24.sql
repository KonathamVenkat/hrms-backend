-- ROLLBACK of V24__salary_one_current.sql. Stop the backend first. Run as the HRMS user, by hand. Then:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '24';  COMMIT;
-- and remove the migration file, or the app will not start. The service lock stays; only the database backstop goes.
DROP INDEX HRMS.UQ_ES_ONE_CURRENT;
