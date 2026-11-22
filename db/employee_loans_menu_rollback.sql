-- UNDO of db/employee_loans_menu.sql: removes the Employee Loans sidebar entry and its rights. Run as the HRMS user.

DELETE FROM HRMS.ROLE_RIGHTS
 WHERE MENU_ID IN (SELECT SUB_MENU_ID FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE = 'PAY_LOANS');

DELETE FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE = 'PAY_LOANS';

COMMIT;
