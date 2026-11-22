-- ROLLBACK of V33__employee_loans.sql. Stop the backend first. Run as the HRMS user, by hand.
-- Drops the loan register (every recorded loan is lost), its sequence and SALARY_COMPONENTS.IS_LOAN, then removes V33
-- from Flyway's history. Also remove the V33 file from db/migration AND go back to the code from before it, or the app
-- will not start. Safe to run twice.
DECLARE
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM user_tables WHERE table_name = 'EMPLOYEE_LOANS';
  IF n > 0 THEN
    EXECUTE IMMEDIATE 'DROP TABLE HRMS.EMPLOYEE_LOANS';
  END IF;
  SELECT COUNT(*) INTO n FROM user_sequences WHERE sequence_name = 'SEQ_EMPLOYEE_LOANS';
  IF n > 0 THEN
    EXECUTE IMMEDIATE 'DROP SEQUENCE HRMS.SEQ_EMPLOYEE_LOANS';
  END IF;
  SELECT COUNT(*) INTO n FROM user_tab_columns WHERE table_name = 'SALARY_COMPONENTS' AND column_name = 'IS_LOAN';
  IF n > 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE HRMS.SALARY_COMPONENTS DROP (IS_LOAN) CASCADE CONSTRAINTS';
  END IF;
END;
/
DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '33';
COMMIT;
