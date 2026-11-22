-- ROLLBACK of V29__job_details_grade_increment.sql. Stop the backend first. Run as the HRMS user, by hand.
-- Drops the increment of every job record and empties GRADE_ID (the V5 column itself stays; the code before V29 does
-- not read it, and an emptied column lets V29 be applied again), then removes V29 from Flyway's history.
-- The grades and increments recorded since V29 are lost. Also remove the V29 file from db/migration AND go back to
-- the code from before it (the entity maps INCREMENT_NO), or the app will not start. Safe to run twice.
DECLARE
  n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM user_tab_columns WHERE table_name = 'EMPLOYEE_JOB_DETAILS' AND column_name = 'INCREMENT_NO';
  IF n > 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE HRMS.EMPLOYEE_JOB_DETAILS DROP (INCREMENT_NO) CASCADE CONSTRAINTS';
  END IF;
  UPDATE HRMS.EMPLOYEE_JOB_DETAILS SET GRADE_ID = NULL WHERE GRADE_ID IS NOT NULL;
END;
/
DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '29';
COMMIT;
