-- Menu for the approver roles. Data only. Safe to run twice: each INSERT skips a row that exists.
-- Run AFTER V25 (the CEO role row). Signed-in users keep their old menu until they sign out and in.
--  1. CEO: the same self-service menu rights an EMPLOYEE has (like general_manager_menu.sql does for the General Manager).
--  2. GENERAL_MANAGER and CEO: My Approvals (the workflow inbox), where the requests they decide are waiting. The older
--     approval screens (leave approval, attendance request approval, overtime approval) list every employee's requests and
--     stay HR_ADMIN/HR_MANAGER only. What each role may actually decide is enforced by the backend, not by this menu.
-- Undo: db/approver_roles_menu_rollback.sql

INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT ceo.ROLE_ID, rr.MAIN_MENU_ID, rr.MENU_ID, rr.CAN_VIEW, rr.CAN_CREATE, rr.CAN_EDIT, rr.CAN_DELETE
  FROM HRMS.ROLE_RIGHTS rr
  JOIN HRMS.ROLES emp ON emp.ROLE_ID = rr.ROLE_ID AND emp.ROLE_NAME = 'EMPLOYEE'
  JOIN HRMS.ROLES ceo ON ceo.ROLE_NAME = 'CEO'
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x
                    WHERE x.ROLE_ID = ceo.ROLE_ID
                      AND x.MAIN_MENU_ID = rr.MAIN_MENU_ID
                      AND NVL(x.MENU_ID, -1) = NVL(rr.MENU_ID, -1));

INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT r.ROLE_ID, s.MAIN_MENU_ID, s.SUB_MENU_ID, 1, 0, 0, 0
  FROM HRMS.ROLES r, HRMS.SUB_MENU s
 WHERE r.ROLE_NAME IN ('GENERAL_MANAGER', 'CEO')
   AND s.SUB_MENU_CODE = 'WORKFLOW_INBOX'
   AND NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x WHERE x.ROLE_ID = r.ROLE_ID AND x.MENU_ID = s.SUB_MENU_ID);

COMMIT;
