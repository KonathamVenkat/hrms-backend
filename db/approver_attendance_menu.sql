-- Menu for the General Manager and CEO: the overtime and attendance-request approval screens.
-- Data only. Safe to run twice: each INSERT skips a row that exists. Run AFTER approver_roles_menu.sql.
-- The screens are the HR ones; the server limits the list to the requests the caller decides (GENERAL_MANAGER: requests
-- from HR_ADMIN/HR_MANAGER; CEO: requests from the GENERAL_MANAGER) and never their own. Rows are found by their screen
-- route, so no menu code has to be known. Signed-in users keep their old menu until they sign out and in.
-- Undo: db/approver_attendance_menu_rollback.sql

INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, s.MAIN_MENU_ID, s.SUB_MENU_ID, 1, 0, 0, 0
  FROM HRMS.ROLES r, HRMS.SUB_MENU s
 WHERE r.ROLE_NAME IN ('GENERAL_MANAGER', 'CEO')
   AND (s.SUB_MENU_ACTION LIKE '%/attendance/overtime-approval' OR s.SUB_MENU_ACTION LIKE '%/attendance/request-approval')
   AND NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x WHERE x.ROLE_ID = r.ROLE_ID AND x.MENU_ID = s.SUB_MENU_ID);
-- Expect: 4 rows (2 roles x 2 screens), 0 on a second run. 0 on the first run means the two menu rows do not exist.

COMMIT;
