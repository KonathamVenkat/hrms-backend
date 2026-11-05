-- The Employee Salary screen (every employee's salary, and the history) is now for HR_ADMIN only: the backend refuses
-- the salary list and the history to an HR_MANAGER. This removes the sidebar entry for the HR_MANAGER role so it does not
-- open a screen that only answers "access denied". Salary components and structures stay visible to HR_MANAGER.
-- Not applied yet. Run as the HRMS user. Undo: db/hr_manager_no_employee_salary_menu_rollback.sql.
-- Safe to run twice: it only removes the one row if it is there.

DELETE FROM HRMS.ROLE_RIGHTS
 WHERE ROLE_ID = (SELECT ROLE_ID FROM HRMS.ROLES WHERE ROLE_NAME = 'HR_MANAGER')
   AND MENU_ID = (SELECT SUB_MENU_ID FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE = 'PAY_EMP_SALARY');

COMMIT;
