-- ROLLBACK of V11__nationality_country_name.sql. Drops the country names of the nationalities (they are lost).
-- Stop the backend first. Run as the HRMS user, by hand. Afterwards remove the history row and the migration file,
-- or the app will not start:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '11';  COMMIT;
-- The code also must go back to the version before this change (the entity maps both columns).
ALTER TABLE HRMS.NATIONALITIES DROP (COUNTRY_NAME, COUNTRY_NAME_AR);
