-- DEVELOPMENT DATA (2026-11-23): the logins created by db/seed_dept_heads_secretaries.sql (CREATED_BY = 'SEEDDH') get the shared
-- test password Welcome123 and are no longer asked to change it at first sign-in (owner's choice for these test employees).
-- Never run on production. No undo: the earlier temporary password is not needed again (re-run the seed generator's hash if so).
UPDATE HRMS.AUTH_USERS
   SET PASSWORD_HASH = '$2a$10$zU8JRLctDJHXkYXGoJ5Sm.fLhr2uSnIwbXnW6MH5jwx3902CFvDda',
       MUST_CHANGE_PASSWORD = 0,
       FAILED_ATTEMPTS = 0,
       IS_LOCKED = FALSE
 WHERE CREATED_BY = 'SEEDDH';
-- Expect: 288 rows.
COMMIT;
