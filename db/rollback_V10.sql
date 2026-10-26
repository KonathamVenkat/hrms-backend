-- ROLLBACK of V10__approval_workflow.sql.
-- DESTROYS every workflow definition, step, route, setting, request history row and department secretary entered
-- since V10, and the workflow columns on leave requests (the requests themselves stay, on the old approval).
-- Take a backup first. Stop the backend first. Run as the HRMS user, by hand, as a script. Children go before parents.
-- Safe to re-run after a partial failure: every statement ignores "object already gone"
-- (ORA-00942 table, ORA-02443 constraint, ORA-01418 index, ORA-00904 column, ORA-02289 sequence, ORA-12987 column).
-- Before running: no AUTH_USERS row may have ROLE = 'GENERAL_MANAGER' (move those users to another role first),
-- otherwise the restored role check cannot be added. The script checks this first and stops (ORA-20010) before
-- changing anything.
-- NOTE: the restored AUTH_USERS role check is the 10-role version that dev had before V10 (it includes SYSTEM_ADMIN),
-- NOT the original 3-role check from V1__baseline.sql.
-- Afterwards remove the history row and the migration file, or the app will not start:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '10';  COMMIT;
-- Also run the rollback block at the bottom of db/workflow_menu.sql if that script was run.

DECLARE
  v_general_managers NUMBER;
  PROCEDURE run(p_sql VARCHAR2) IS
  BEGIN
    EXECUTE IMMEDIATE p_sql;
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLCODE NOT IN (-942, -2443, -1418, -904, -2289, -12987) THEN
        RAISE;
      END IF;
  END;
BEGIN
  -- 0. Stop before any change while a login still has the role: dropping the check and failing to re-add it would
  --    leave AUTH_USERS without any role check.
  SELECT COUNT(*) INTO v_general_managers FROM HRMS.AUTH_USERS WHERE "ROLE" = 'GENERAL_MANAGER';
  IF v_general_managers > 0 THEN
    RAISE_APPLICATION_ERROR(-20010, v_general_managers || ' AUTH_USERS row(s) still have ROLE = GENERAL_MANAGER. '
      || 'Move them to another role, then run this script again. Nothing was changed.');
  END IF;

  -- 6. Role: remove its rights and row, restore the 10-role check.
  run('DELETE FROM HRMS.ROLE_RIGHTS WHERE ROLE_ID IN (SELECT ROLE_ID FROM HRMS.ROLES WHERE ROLE_NAME = ''GENERAL_MANAGER'')');
  run('DELETE FROM HRMS.ROLES WHERE ROLE_NAME = ''GENERAL_MANAGER''');
  run('ALTER TABLE HRMS.AUTH_USERS DROP CONSTRAINT CK_AUTH_USERS_ROLE');
  run('ALTER TABLE HRMS.AUTH_USERS ADD CONSTRAINT CK_AUTH_USERS_ROLE CHECK ("ROLE" IN '
      || '(''SYSTEM_ADMIN'',''HR_ADMIN'',''HR_MANAGER'',''EMPLOYEE'',''REPORTING_MANAGER'','
      || '''ATTENDANCE_OFFICER'',''PAYROLL_OFFICER'',''PAYROLL_APPROVER'',''FINANCE_USER'',''AUDITOR''))');

  -- 5. Leave request columns.
  run('DROP INDEX HRMS.IDX_LR_STAGE');
  run('DROP INDEX HRMS.IDX_LR_CUR_ROLE');
  run('DROP INDEX HRMS.IDX_LR_CUR_APPROVER');
  run('DROP INDEX HRMS.IDX_LR_TARGET_DEPT');
  run('DROP INDEX HRMS.IDX_LR_WF_DEFINITION');
  run('ALTER TABLE HRMS.LEAVE_REQUESTS DROP CONSTRAINT FK_LR_CUR_APPROVER');
  run('ALTER TABLE HRMS.LEAVE_REQUESTS DROP CONSTRAINT FK_LR_TARGET_DEPT');
  run('ALTER TABLE HRMS.LEAVE_REQUESTS DROP CONSTRAINT FK_LR_WF_DEFINITION');
  run('ALTER TABLE HRMS.LEAVE_REQUESTS DROP CONSTRAINT CHK_LR_STAGE');
  run('ALTER TABLE HRMS.LEAVE_REQUESTS DROP COLUMN CURRENT_APPROVER_ROLE');
  run('ALTER TABLE HRMS.LEAVE_REQUESTS DROP COLUMN CURRENT_APPROVER_EMPLOYEE_ID');
  run('ALTER TABLE HRMS.LEAVE_REQUESTS DROP COLUMN CURRENT_STEP_KEY');
  run('ALTER TABLE HRMS.LEAVE_REQUESTS DROP COLUMN TARGET_DEPARTMENT_ID');
  run('ALTER TABLE HRMS.LEAVE_REQUESTS DROP COLUMN STAGE');
  run('ALTER TABLE HRMS.LEAVE_REQUESTS DROP COLUMN FLOWABLE_PROCESS_INSTANCE_ID');
  run('ALTER TABLE HRMS.LEAVE_REQUESTS DROP COLUMN WORKFLOW_DEFINITION_ID');

  -- 4, 3. Tables, children first.
  run('DROP TABLE HRMS.REQUEST_HISTORY');
  run('DROP TABLE HRMS.WORKFLOW_SETTINGS');
  run('DROP TABLE HRMS.WORKFLOW_ROUTES');
  run('DROP TABLE HRMS.WORKFLOW_STEPS');
  run('DROP TABLE HRMS.WORKFLOW_DEFINITIONS');

  -- 2. Department secretary.
  run('DROP INDEX HRMS.IDX_DEPT_SECRETARY_ID');
  run('ALTER TABLE HRMS.DEPARTMENTS DROP CONSTRAINT FK_DEPT_SECRETARY');
  run('ALTER TABLE HRMS.DEPARTMENTS DROP COLUMN SECRETARY_EMPLOYEE_ID');

  -- 1. Sequences.
  run('DROP SEQUENCE HRMS.SEQ_REQUEST_HISTORY');
  run('DROP SEQUENCE HRMS.SEQ_WORKFLOW_ROUTES');
  run('DROP SEQUENCE HRMS.SEQ_WORKFLOW_STEPS');
  run('DROP SEQUENCE HRMS.SEQ_WORKFLOW_DEFINITIONS');
END;
/

COMMIT;
