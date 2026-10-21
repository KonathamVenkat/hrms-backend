-- ROLLBACK of V8__organisation_branches.sql.
-- DESTROYS every branch location, branch manager and employee-branch assignment entered since V8 (the columns are dropped).
-- Take a backup first. Stop the backend first. Run as the HRMS user, by hand. Children go before parents.
-- Afterwards remove the history row and the migration file, or the app will not start:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '8';  COMMIT;
-- If later scripts (V9+) use these columns, roll those back first.
-- Also run db/organisation_menu_rollback.sql if db/organisation_menu.sql was run.

DROP INDEX HRMS.IDX_EJD_BRANCH_ID;
ALTER TABLE HRMS.EMPLOYEE_JOB_DETAILS DROP CONSTRAINT FK_EJD_BRANCH;
ALTER TABLE HRMS.EMPLOYEE_JOB_DETAILS DROP COLUMN COMPANY_BRANCH_ID;

DROP INDEX HRMS.IDX_CB_MANAGER_ID;
DROP INDEX HRMS.IDX_CB_LOCATION_ID;
ALTER TABLE HRMS.COMPANY_BRANCHES DROP CONSTRAINT FK_CB_MANAGER;
ALTER TABLE HRMS.COMPANY_BRANCHES DROP CONSTRAINT FK_CB_LOCATION;
ALTER TABLE HRMS.COMPANY_BRANCHES DROP COLUMN BRANCH_MANAGER_EMPLOYEE_ID;
ALTER TABLE HRMS.COMPANY_BRANCHES DROP COLUMN LOCATION_ID;
