-- ROLLBACK of V23__refresh_token_rotated_at.sql. Stop the backend first. Run as the HRMS user, by hand. Then:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '23';  COMMIT;
-- and remove the migration file, or the app will not start. Deploy the previous backend build first: it does not use the column.
ALTER TABLE HRMS.AUTH_REFRESH_TOKENS DROP (ROTATED_AT);
