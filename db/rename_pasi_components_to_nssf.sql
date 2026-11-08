-- The two statutory salary components still carry the Omani name PASI while the flag on them is "NSSF applicable".
-- This renames their codes and English names. The Arabic names, the rates (7% and 1%) and everything else are NOT touched:
-- the rates are the old Omani figures, so check them against the South Sudan NSSF rules before payroll is run.
-- No structure uses these two components yet (checked 2026-11-08), so nothing else depends on the codes.
-- Not applied yet. Run as the HRMS user. Safe to run twice (it only changes rows that still have the old code).
-- Undo: swap the codes back by hand (NSSF_EMP -> PASI_EMP, NSSF_EMP_ILC -> PASI_EMP_ILC) with the old English names.

UPDATE HRMS.SALARY_COMPONENTS
   SET COMPONENT_CODE = 'NSSF_EMP', COMPONENT_NAME = 'NSSF - Employee (7%)', UPDATED_AT = SYSTIMESTAMP
 WHERE COMPONENT_CODE = 'PASI_EMP';

UPDATE HRMS.SALARY_COMPONENTS
   SET COMPONENT_CODE = 'NSSF_EMP_ILC', COMPONENT_NAME = 'NSSF - ILC Employee (1%)', UPDATED_AT = SYSTIMESTAMP
 WHERE COMPONENT_CODE = 'PASI_EMP_ILC';

COMMIT;
