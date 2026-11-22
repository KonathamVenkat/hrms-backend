-- Marks the 17 advance components of the March 2026 salary sheet as loan types (SALARY_COMPONENTS.IS_LOAN = 1), so
-- they are offered on Payroll > Employee Loans. Run AFTER V33, as HRMS. Safe to run twice.
-- Not loan types: ADVANCE (generic salary advance), PENALTY, ZAKAT, MINISTER_DED and the statutory items.
-- Check afterwards (expect 17):  SELECT COUNT(*) FROM HRMS.SALARY_COMPONENTS WHERE IS_LOAN = 1;
-- Undo: db/loan_types_mar2026_rollback.sql
UPDATE HRMS.SALARY_COMPONENTS
   SET IS_LOAN = 1, UPDATED_AT = SYSTIMESTAMP
 WHERE IS_LOAN = 0
   AND COMPONENT_TYPE = 'DEDUCTION'
   AND COMPONENT_CODE IN ('ADV_EMERGENCY', 'ADV_EMERGENCY_2', 'ADV_CAR', 'ADV_CAR_2', 'ADV_MOTORCYCLE',
                          'ADV_BUILDING_1', 'ADV_BUILDING_2', 'HOUSE_MORTGAGE', 'ADV_MEDIUM_TERM', 'ADV_MARRIAGE',
                          'ADV_HOME_MAINT', 'ADV_CAR_MAINT', 'ADV_HOME_IMPROVE', 'LAND_PURCHASE',
                          'ADV_HOME_MAINT_MAJOR', 'ADV_CAR_INSURANCE', 'ADV_LONG_TERM');
COMMIT;
