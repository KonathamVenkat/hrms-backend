-- ROLLBACK of V12__grade_levels.sql. Drops the GRADE_LEVELS table and its sequence (the rows are lost).
-- Stop the backend first. Run as the HRMS user, by hand. Afterwards remove the history row and the migration file,
-- or the app will not start:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '12';  COMMIT;
-- Safe only while nothing references GRADE_LEVELS (no code or foreign key does yet).
DROP TABLE HRMS.GRADE_LEVELS;
DROP SEQUENCE HRMS.SEQ_GRADE_LEVELS;
