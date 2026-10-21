-- Dev seed: give a few departments manager/executive-grade people so the Reporting / Functional Manager
-- pickers on Employee > Job Details have something to show.
--
-- GET /api/v1/departments/{id}/managers lists ACTIVE employees whose CURRENT job is in that department
-- and whose designation grade starts with M or E. Before this script only HR (HR Manager, M1) qualified.
--   IT  (100): konatham venkat   (1018)  System Analyst P2       -> IT Manager M1
--   PMO (108): Konatham Jaya     (1020)  Senior Project Manager  -> PMO Director E1
--   HR  (101): HR Manager        (1013)  already HR Manager M1   (no change)
-- It edits the CURRENT job row in place (a dev shortcut, no new history row). Safe to re-run.
-- Run as the HRMS user, then check the SELECT at the bottom.

UPDATE HRMS.EMPLOYEE_JOB_DETAILS
   SET DESIGNATION_ID = (SELECT DESIG_ID FROM HRMS.DESIGNATIONS WHERE CODE = 'IT_MGR'),
       UPDATED_AT     = CURRENT_TIMESTAMP
 WHERE EMPLOYEE_ID = 1018 AND IS_CURRENT = 1 AND DEPARTMENT_ID = 100;

UPDATE HRMS.EMPLOYEE_JOB_DETAILS
   SET DESIGNATION_ID = (SELECT DESIG_ID FROM HRMS.DESIGNATIONS WHERE CODE = 'PMO_DIR'),
       UPDATED_AT     = CURRENT_TIMESTAMP
 WHERE EMPLOYEE_ID = 1020 AND IS_CURRENT = 1 AND DEPARTMENT_ID = 108;

COMMIT;

-- Expect: IT -> 1018 (M1), HR -> 1013 (M1), PMO -> 1020 (E1)
SELECT d.CODE AS DEPT, e.EMPLOYEE_CODE, e.FIRST_NAME || ' ' || e.LAST_NAME AS NAME, ds.TITLE, ds.GRADE_LEVEL
  FROM HRMS.EMPLOYEE_JOB_DETAILS j
  JOIN HRMS.EMPLOYEES e    ON e.EMPLOYEE_ID = j.EMPLOYEE_ID AND e.IS_ACTIVE = 1
  JOIN HRMS.DESIGNATIONS ds ON ds.DESIG_ID  = j.DESIGNATION_ID
  JOIN HRMS.DEPARTMENTS d   ON d.DEPT_ID    = j.DEPARTMENT_ID
 WHERE j.IS_CURRENT = 1 AND (ds.GRADE_LEVEL LIKE 'M%' OR ds.GRADE_LEVEL LIKE 'E%')
 ORDER BY d.CODE, ds.GRADE_LEVEL;
