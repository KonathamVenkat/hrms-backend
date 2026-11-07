-- Undo db/approver_attendance_menu.sql.
DELETE FROM HRMS.ROLE_RIGHTS
 WHERE ROLE_ID IN (SELECT ROLE_ID FROM HRMS.ROLES WHERE ROLE_NAME IN ('GENERAL_MANAGER', 'CEO'))
   AND MENU_ID IN (SELECT SUB_MENU_ID FROM HRMS.SUB_MENU
                    WHERE SUB_MENU_ACTION LIKE '%/attendance/overtime-approval'
                       OR SUB_MENU_ACTION LIKE '%/attendance/request-approval');
COMMIT;
