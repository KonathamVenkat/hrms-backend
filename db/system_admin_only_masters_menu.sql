-- Document types, office locations, work shifts, leave types and the holiday calendar are maintained by SYSTEM_ADMIN only:
-- the backend now refuses their admin lists and every change to HR_ADMIN (HR roles keep the read-only dropdown lists the
-- other screens use). This removes the five sidebar entries for the HR_ADMIN role so it does not open screens that only
-- answer "access denied". SYSTEM_ADMIN keeps them.
-- Not applied yet. Run as the HRMS user. Undo: db/system_admin_only_masters_menu_rollback.sql.
-- Safe to run twice: it only removes the rows that are there.

DELETE FROM HRMS.ROLE_RIGHTS
 WHERE ROLE_ID = (SELECT ROLE_ID FROM HRMS.ROLES WHERE ROLE_NAME = 'HR_ADMIN')
   AND MENU_ID IN (SELECT SUB_MENU_ID FROM HRMS.SUB_MENU
                    WHERE SUB_MENU_ACTION IN ('/app/admin/document-types', '/app/admin/office-locations',
                                              '/app/admin/work-shifts', '/app/admin/leave-types',
                                              '/app/admin/holiday-calendar'));

COMMIT;
