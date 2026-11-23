-- UNDO of db/seed_workflow_test_setup.sql (the GM/CEO password is not restored: the old one was a temporary test password).
-- Balances that leave requests already moved (PENDING/USED above 0) are kept: delete those requests first if needed.
UPDATE HRMS.COMPANY_BRANCHES SET BRANCH_MANAGER_EMPLOYEE_ID = NULL
 WHERE COMPANY_BRANCHES_CODE = 'JUBA_HO'
   AND BRANCH_MANAGER_EMPLOYEE_ID = (SELECT HEAD_EMPLOYEE_ID FROM HRMS.DEPARTMENTS WHERE CODE = 'DEP-061');
DELETE FROM HRMS.LEAVE_BALANCES WHERE CREATED_BY = 'SEEDWF' AND PENDING_DAYS = 0 AND USED_DAYS = 0;
COMMIT;
