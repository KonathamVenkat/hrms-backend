-- Undo of db/loan_types_mar2026.sql: the 17 advance components are no longer offered as loan types. Loans already
-- recorded keep their type.
UPDATE HRMS.SALARY_COMPONENTS
   SET IS_LOAN = 0, UPDATED_AT = SYSTIMESTAMP
 WHERE IS_LOAN = 1
   AND COMPONENT_CODE IN ('ADV_EMERGENCY', 'ADV_EMERGENCY_2', 'ADV_CAR', 'ADV_CAR_2', 'ADV_MOTORCYCLE',
                          'ADV_BUILDING_1', 'ADV_BUILDING_2', 'HOUSE_MORTGAGE', 'ADV_MEDIUM_TERM', 'ADV_MARRIAGE',
                          'ADV_HOME_MAINT', 'ADV_CAR_MAINT', 'ADV_HOME_IMPROVE', 'LAND_PURCHASE',
                          'ADV_HOME_MAINT_MAJOR', 'ADV_CAR_INSURANCE', 'ADV_LONG_TERM');
COMMIT;
