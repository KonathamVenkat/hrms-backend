-- V24: one current salary per employee, enforced by the database as well as the service.
-- Until now two simultaneous salary assignments for the same employee could both insert an IS_CURRENT = 1 row, after which
-- every lookup of that employee's current salary failed. Same pattern as UQ_EJD_ONE_CURRENT on EMPLOYEE_JOB_DETAILS.
-- Not applied yet. Run by hand on HRMS with the backend stopped (Flyway is validate-only), then record it with
-- db/flyway_record_v24.sql. Rollback: db/rollback_V24.sql.
-- BEFORE running, look for employees that already have two current rows; the index cannot be built while any exist:
--   SELECT EMPLOYEE_ID, COUNT(*) FROM HRMS.EMPLOYEE_SALARY WHERE IS_CURRENT = 1 GROUP BY EMPLOYEE_ID HAVING COUNT(*) > 1;
-- Fix each one (set IS_CURRENT = 0 on the older record), then run this script.
CREATE UNIQUE INDEX HRMS.UQ_ES_ONE_CURRENT ON HRMS.EMPLOYEE_SALARY (CASE WHEN IS_CURRENT = 1 THEN EMPLOYEE_ID END);
