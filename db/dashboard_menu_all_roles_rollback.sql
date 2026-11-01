-- Removes the Dashboard rights added by db/dashboard_menu_all_roles.sql from every role except the two that had it before
-- (EMPLOYEE and HR_MANAGER).
DELETE FROM HRMS.ROLE_RIGHTS
 WHERE MAIN_MENU_ID = 100 AND MENU_ID IS NULL
   AND ROLE_ID NOT IN (SELECT ROLE_ID FROM HRMS.ROLES WHERE ROLE_NAME IN ('EMPLOYEE', 'HR_MANAGER'));
COMMIT;
