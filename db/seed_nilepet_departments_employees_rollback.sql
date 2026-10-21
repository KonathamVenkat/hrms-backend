-- UNDO of db/seed_nilepet_departments_employees.sql: removes the sample employees (created_by SEED), their logins and job
-- records, the department heads it set, and the 10 new departments and 40 new designations.
-- Run as the HRMS user. Rows other data refers to (leave requests, attendance, documents, salary...) make Oracle refuse
-- the delete (ORA-02292): remove or move that data first. Order: dependants before parents.

UPDATE HRMS.DEPARTMENTS SET HEAD_EMPLOYEE_ID = NULL
 WHERE HEAD_EMPLOYEE_ID IN (SELECT EMPLOYEE_ID FROM HRMS.EMPLOYEES WHERE CREATED_BY = 'SEED');
DELETE FROM HRMS.AUTH_USERS WHERE CREATED_BY = 'SEED';
DELETE FROM HRMS.EMPLOYEE_JOB_DETAILS WHERE CREATED_BY = 'SEED';
DELETE FROM HRMS.EMPLOYEES WHERE CREATED_BY = 'SEED';
DELETE FROM HRMS.DESIGNATIONS WHERE CODE IN ('HSE_MGR', 'HSE_OFF', 'ENV_SPEC', 'SAFE_INSP', 'FIRE_COORD', 'RET_OPS_MGR', 'STN_MGR', 'SHIFT_SUP', 'PUMP_ATT', 'CASHIER', 'DEPOT_MGR', 'TERM_SUP', 'DEPOT_OP', 'LOAD_MASTER', 'STOCK_CTRL', 'STOREKEEPER', 'SCM_MGR', 'PROC_SPEC', 'INV_PLAN', 'LOG_PLAN', 'ENG_MGR', 'MECH_ENG', 'ELEC_ENG', 'INSTR_TECH', 'MAINT_PLAN', 'TRD_MGR', 'SUPPLY_ANLT', 'PRICE_ANLT', 'AUD_HEAD', 'SR_AUD', 'AUDITOR', 'COMMS_MGR', 'PR_OFF', 'SEC_MGR', 'SEC_SUP', 'SEC_OFF', 'FLT_MGR', 'TRANSP_COORD', 'TANKER_DRV', 'WORKSHOP_MECH');
DELETE FROM HRMS.DEPARTMENTS WHERE CODE IN ('HSE', 'RETAIL', 'DEPOT', 'SCM', 'ENGMNT', 'TRADE', 'AUDIT', 'COMMS', 'SEC', 'FLEET');
COMMIT;
