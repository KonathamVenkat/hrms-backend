-- UNDO of db/hr_manager_no_employee_salary_menu.sql: gives the HR_MANAGER role its Employee Salary sidebar entry back (view only).
-- The backend would still refuse the salary list and history to HR_MANAGER, so only run this together with undoing that change.
-- Run as the HRMS user. Safe to run twice.
INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, sm.MAIN_MENU_ID, sm.SUB_MENU_ID, 1, 0, 0, 0
  FROM HRMS.ROLES r, HRMS.SUB_MENU sm
 WHERE r.ROLE_NAME = 'HR_MANAGER'
   AND sm.SUB_MENU_CODE = 'PAY_EMP_SALARY'
   AND NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x WHERE x.ROLE_ID = r.ROLE_ID AND x.MENU_ID = sm.SUB_MENU_ID);

COMMIT;
