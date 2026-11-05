-- ROLLBACK of V22__identity_unique_numbers.sql. Stop the backend first. Run as the HRMS user, by hand. Then:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '22';  COMMIT;
-- and remove the migration file, or the app will not start. The service still checks duplicates; only the database backstop goes.
DROP INDEX HRMS.UQ_EII_NATIONAL_ID;
DROP INDEX HRMS.UQ_EII_PASSPORT;
DROP INDEX HRMS.UQ_EII_VISA_NUMBER;
DROP INDEX HRMS.UQ_EII_WORK_PERMIT;
