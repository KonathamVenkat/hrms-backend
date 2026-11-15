-- ROLLBACK of V28__salary_grade_scale.sql. Stop the backend first. Run as the HRMS user, by hand.
-- Drops the pay scale table and everything loaded into it (db/load_salary_grade_scale_mar2026.sql), then removes V28
-- from Flyway's history. Also remove the V28 file from db/migration (or go back to code from before it), or the app will
-- not start.
DECLARE
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM user_tables WHERE table_name = 'SALARY_GRADE_SCALE';
  IF n > 0 THEN
    EXECUTE IMMEDIATE 'DROP TABLE HRMS.SALARY_GRADE_SCALE';
  END IF;
END;
/
DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '28';
COMMIT;
