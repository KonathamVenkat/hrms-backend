-- GENERAL_MANAGER: the same self-service menu rights an EMPLOYEE has (the role also counts as EMPLOYEE in the
-- backend, RoleHierarchy). Approvals comes from workflow_menu.sql (run that too; the order does not matter).
-- Data only. Safe to run twice: each INSERT skips a row that exists.
-- Run AFTER V10 (the GENERAL_MANAGER role row). Signed-in users keep their old menu until they sign out and in.

INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE)
SELECT gm.ROLE_ID, rr.MAIN_MENU_ID, rr.MENU_ID, rr.CAN_VIEW, rr.CAN_CREATE, rr.CAN_EDIT, rr.CAN_DELETE
  FROM HRMS.ROLE_RIGHTS rr
  JOIN HRMS.ROLES emp ON emp.ROLE_ID = rr.ROLE_ID AND emp.ROLE_NAME = 'EMPLOYEE'
  JOIN HRMS.ROLES gm  ON gm.ROLE_NAME = 'GENERAL_MANAGER'
 WHERE NOT EXISTS (SELECT 1 FROM HRMS.ROLE_RIGHTS x
                    WHERE x.ROLE_ID = gm.ROLE_ID
                      AND x.MAIN_MENU_ID = rr.MAIN_MENU_ID
                      AND NVL(x.MENU_ID, -1) = NVL(rr.MENU_ID, -1));
-- Expect: one row per EMPLOYEE right, 0 on a second run.

COMMIT;

-- Check afterwards:
-- SELECT COUNT(*) FROM HRMS.ROLE_RIGHTS WHERE ROLE_ID = (SELECT ROLE_ID FROM HRMS.ROLES WHERE ROLE_NAME = 'GENERAL_MANAGER');

-- To undo:
-- DELETE FROM HRMS.ROLE_RIGHTS WHERE ROLE_ID = (SELECT ROLE_ID FROM HRMS.ROLES WHERE ROLE_NAME = 'GENERAL_MANAGER');
-- COMMIT;
