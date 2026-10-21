-- ROLLBACK of V9__language_level_sort_order.sql. Drops the sort order of the language levels (the order is lost).
-- Stop the backend first. Run as the HRMS user, by hand. Afterwards remove the history row and the migration file,
-- or the app will not start:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '9';  COMMIT;
-- The code also must go back to the version before this change (the entity maps SORT_ORDER).
ALTER TABLE HRMS.LANGUAGE_LEVELS DROP COLUMN SORT_ORDER;
