-- UNDO of db/seed_dept_heads_secretaries.sql: clears the head/secretary slots it filled, removes its employees
-- (CREATED_BY = 'SEEDDH'), their logins and job records, and the designations it added (codes SDH-...).
-- Run as the HRMS user. Rows other data refers to (leave requests, attendance, documents...) make Oracle refuse the delete
-- (ORA-02292): remove or move that data first.
UPDATE HRMS.DEPARTMENTS SET HEAD_EMPLOYEE_ID = NULL
 WHERE HEAD_EMPLOYEE_ID IN (SELECT EMPLOYEE_ID FROM HRMS.EMPLOYEES WHERE CREATED_BY = 'SEEDDH');
UPDATE HRMS.DEPARTMENTS SET SECRETARY_EMPLOYEE_ID = NULL
 WHERE SECRETARY_EMPLOYEE_ID IN (SELECT EMPLOYEE_ID FROM HRMS.EMPLOYEES WHERE CREATED_BY = 'SEEDDH');
DELETE FROM HRMS.AUTH_USERS WHERE CREATED_BY = 'SEEDDH';
DELETE FROM HRMS.EMPLOYEE_JOB_DETAILS WHERE CREATED_BY = 'SEEDDH';
DELETE FROM HRMS.EMPLOYEES WHERE CREATED_BY = 'SEEDDH';
DELETE FROM HRMS.DESIGNATIONS WHERE CODE LIKE 'SDH-%';
COMMIT;
