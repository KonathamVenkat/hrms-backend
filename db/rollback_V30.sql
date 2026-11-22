-- ROLLBACK of V30__salary_structure_grade.sql. Stop the backend first. Run as the HRMS user, by hand.
-- Drops the grade link of every salary structure, then removes V30 from Flyway's history. Also remove the V30 file
-- from db/migration AND go back to the code from before it (the entity maps GRADE_ID), or the app will not start.
-- Safe to run twice.
DECLARE
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM user_tab_columns WHERE table_name = 'SALARY_STRUCTURES' AND column_name = 'GRADE_ID';
  IF n > 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE HRMS.SALARY_STRUCTURES DROP (GRADE_ID) CASCADE CONSTRAINTS';
  END IF;
END;
/
DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '30';
COMMIT;
