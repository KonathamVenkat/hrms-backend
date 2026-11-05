-- ROLLBACK of V25__ceo_role.sql. Stop the backend first. Run as the HRMS user, by hand. Then:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '25';  COMMIT;
-- and remove the migration file, or the app will not start. Deploy the previous backend build first.
-- Stops before any change while a login still has the role: dropping the check and failing to re-add it would leave
-- AUTH_USERS without any role check.
DECLARE
  v_ceos NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_ceos FROM HRMS.AUTH_USERS WHERE "ROLE" = 'CEO';
  IF v_ceos > 0 THEN
    RAISE_APPLICATION_ERROR(-20011, v_ceos || ' AUTH_USERS row(s) still have ROLE = CEO. '
      || 'Move them to another role, then run this script again. Nothing was changed.');
  END IF;
  EXECUTE IMMEDIATE 'DELETE FROM HRMS.ROLE_RIGHTS WHERE ROLE_ID IN (SELECT ROLE_ID FROM HRMS.ROLES WHERE ROLE_NAME = ''CEO'')';
  EXECUTE IMMEDIATE 'DELETE FROM HRMS.ROLES WHERE ROLE_NAME = ''CEO''';
  EXECUTE IMMEDIATE 'ALTER TABLE HRMS.AUTH_USERS DROP CONSTRAINT CK_AUTH_USERS_ROLE';
  EXECUTE IMMEDIATE q'[ALTER TABLE HRMS.AUTH_USERS ADD CONSTRAINT CK_AUTH_USERS_ROLE CHECK ("ROLE" IN
    ('SYSTEM_ADMIN','HR_ADMIN','HR_MANAGER','GENERAL_MANAGER','EMPLOYEE','REPORTING_MANAGER',
     'ATTENDANCE_OFFICER','PAYROLL_OFFICER','PAYROLL_APPROVER','FINANCE_USER','AUDITOR'))]';
END;
/
COMMIT;
