-- Undo db/deactivate_nssf_components.sql: switches NSSF_EMP and NSSF_EMP_ILC back on.
-- Run as the HRMS user. Ends with COMMIT. Afterwards do not put them in the same structure as INSURANCE.

UPDATE HRMS.SALARY_COMPONENTS
   SET IS_ACTIVE = 1, VERSION = VERSION + 1, UPDATED_AT = SYSTIMESTAMP, UPDATED_BY = 'SYSTEM'
 WHERE COMPONENT_CODE IN ('NSSF_EMP', 'NSSF_EMP_ILC') AND IS_ACTIVE = 0;
-- Expect: 2 rows updated.

COMMIT;
