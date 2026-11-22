-- ROLLBACK of V32__qualification_levels_salary_allowances.sql. Stop the backend first. Run as the HRMS user, by hand.
-- Drops the Social and Qualification columns of EMPLOYEE_SALARY (those allowances are lost from every salary record;
-- their gross and net stay as saved), the QUALIFICATION_LEVELS master and its sequence, then removes V32 from Flyway's
-- history. Also remove the V32 file from db/migration AND go back to the code from before it, or the app will not
-- start. Safe to run twice.
DECLARE
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM user_tab_columns WHERE table_name = 'EMPLOYEE_SALARY' AND column_name = 'SOCIAL_AMOUNT';
  IF n > 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE HRMS.EMPLOYEE_SALARY DROP (SOCIAL_AMOUNT, QUALIFICATION_LEVEL_ID, QUALIFICATION_AMOUNT) CASCADE CONSTRAINTS';
  END IF;
  SELECT COUNT(*) INTO n FROM user_tables WHERE table_name = 'QUALIFICATION_LEVELS';
  IF n > 0 THEN
    EXECUTE IMMEDIATE 'DROP TABLE HRMS.QUALIFICATION_LEVELS';
  END IF;
  SELECT COUNT(*) INTO n FROM user_sequences WHERE sequence_name = 'SEQ_QUALIFICATION_LEVELS';
  IF n > 0 THEN
    EXECUTE IMMEDIATE 'DROP SEQUENCE HRMS.SEQ_QUALIFICATION_LEVELS';
  END IF;
END;
/
DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '32';
COMMIT;
