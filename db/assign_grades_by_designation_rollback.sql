-- UNDO of db/assign_grades_by_designation.sql: removes the grade and increment it set (rows marked UPDATED_BY = 'GRADEMAP').
-- A row edited on screen afterwards has another UPDATED_BY and is left alone. Salaries already assigned against these grades
-- are not touched: check Payroll > Employee Salary before running this. Run as the HRMS user.
UPDATE HRMS.EMPLOYEE_JOB_DETAILS
   SET GRADE_ID = NULL, INCREMENT_NO = NULL, UPDATED_AT = SYSTIMESTAMP, VERSION = NVL(VERSION, 0) + 1, UPDATED_BY = 'GRADEMAP_UNDO'
 WHERE UPDATED_BY = 'GRADEMAP';
COMMIT;
