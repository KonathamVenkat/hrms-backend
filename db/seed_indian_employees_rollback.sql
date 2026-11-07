-- UNDO of db/seed_indian_employees.sql: removes the 22 sample employees (created by SEEDIN), their logins and job records,
-- the department heads it set, and the 9 designations it added.
-- Run as the HRMS user. Rows other data refers to (leave requests, attendance, documents...) make Oracle refuse the delete
-- (ORA-02292): remove or move that data first. Order: dependants before parents.
UPDATE HRMS.DEPARTMENTS SET HEAD_EMPLOYEE_ID = NULL
 WHERE HEAD_EMPLOYEE_ID IN (SELECT EMPLOYEE_ID FROM HRMS.EMPLOYEES WHERE CREATED_BY = 'SEEDIN');
DELETE FROM HRMS.AUTH_USERS WHERE CREATED_BY = 'SEEDIN';
DELETE FROM HRMS.EMPLOYEE_JOB_DETAILS WHERE CREATED_BY = 'SEEDIN';
DELETE FROM HRMS.EMPLOYEES WHERE CREATED_BY = 'SEEDIN';
DELETE FROM HRMS.DESIGNATIONS WHERE CODE IN ('GM', 'BAPP_MGR', 'BAPP_SR_ENG', 'BAPP_ENG', 'BAPP_BA', 'BAPP_DBA', 'BAPP_QA', 'BAPP_SUP', 'PROC_OFFICER');
COMMIT;
