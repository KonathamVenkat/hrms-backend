-- Insurance 8% of gross (component INSURANCE, from the March 2026 salary sheet) is the employee social-insurance deduction.
-- The older pair NSSF_EMP (7% of basic) + NSSF_EMP_ILC (1% of basic) did the same job on a different base, so it is
-- switched off: an inactive component cannot be put into a salary structure, and nothing is deleted.
-- No salary structure uses these two (checked 2026-11-15: 0 structures, 0 structure items).
-- Run as the HRMS user. Safe to run twice (only active rows change). Ends with COMMIT.
-- Undo: db/deactivate_nssf_components_rollback.sql

UPDATE HRMS.SALARY_COMPONENTS
   SET IS_ACTIVE = 0, VERSION = VERSION + 1, UPDATED_AT = SYSTIMESTAMP, UPDATED_BY = 'SYSTEM'
 WHERE COMPONENT_CODE IN ('NSSF_EMP', 'NSSF_EMP_ILC') AND IS_ACTIVE = 1;
-- Expect: 2 rows updated on the first run, 0 on a second run.

COMMIT;

-- Check afterwards:
-- SELECT COMPONENT_CODE, IS_ACTIVE FROM HRMS.SALARY_COMPONENTS WHERE COMPONENT_CODE IN ('NSSF_EMP', 'NSSF_EMP_ILC', 'INSURANCE');
-- Expect: NSSF_EMP 0, NSSF_EMP_ILC 0, INSURANCE 1.
