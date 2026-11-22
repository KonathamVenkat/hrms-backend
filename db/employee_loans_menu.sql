-- Sidebar entry Payroll > Employee Loans (/app/payroll/loans) for HR_ADMIN, who records and closes loans (the backend
-- refuses every other role, like the salary screens). Run as the HRMS user, after V33. Data only. Safe to run twice.
-- Undo: db/employee_loans_menu_rollback.sql

INSERT INTO HRMS.SUB_MENU (SUB_MENU_CODE, SUB_MENU_NAME, SUB_MENU_ACTION, SUB_MENU_TYPE, MAIN_MENU_ID, SORT_ORDER, IS_ACTIVE)
SELECT 'PAY_LOANS', 'Employee Loans', '/app/payroll/loans', 1, mm.MAIN_MENU_ID, 4, 1
  FROM HRMS.MAIN_MENU mm
 WHERE mm.MAIN_MENU_NAME = 'Payroll'
   AND NOT EXISTS (SELECT 1 FROM HRMS.SUB_MENU s WHERE s.SUB_MENU_CODE = 'PAY_LOANS');
-- Expect: 1 row inserted (0 on a second run).

INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, s.MAIN_MENU_ID, s.SUB_MENU_ID, 1, 1, 1, 0
  FROM HRMS.ROLES r, HRMS.SUB_MENU s
 WHERE r.ROLE_NAME = 'HR_ADMIN'
   AND s.SUB_MENU_CODE = 'PAY_LOANS'
   AND NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x WHERE x.ROLE_ID = r.ROLE_ID AND x.MENU_ID = s.SUB_MENU_ID);
-- Expect: 1 row inserted (0 on a second run). Sign out and in again to see it.

COMMIT;
