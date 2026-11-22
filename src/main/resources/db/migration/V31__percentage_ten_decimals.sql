-- V31: percentages with 10 decimals, so a rate such as Armed Forces Support (1/30 of gross in the salary sheet,
-- =Q/30) can be stored as 3.3333333333 instead of the 3.33 that two decimals allowed (76.61 SSP a month less on a
-- 2.3 million gross). At 10 decimals the result agrees with gross/30 at the 3 decimals the calculation keeps.
-- SALARY_STRUCTURE_ITEMS.PERCENTAGE NUMBER(6,2) -> NUMBER(14,10); SALARY_COMPONENTS.DEFAULT_VALUE (a fixed amount or
-- a percentage) NUMBER(12,2) -> NUMBER(20,10). The digits before the decimal point stay the same (4 and 10), so every
-- stored value fits unchanged; Oracle allows this on columns that hold data. The CHECK 0..100 stays as it is.
-- db/armed_forces_one_thirtieth.sql then sets the Armed Forces rate. Not applied yet. Run by hand on HRMS with the
-- backend stopped (Flyway is validate-only), then record it with db/flyway_record_v31.sql. Rollback: db/rollback_V31.sql.
ALTER TABLE HRMS.SALARY_STRUCTURE_ITEMS MODIFY (PERCENTAGE NUMBER(14,10));
ALTER TABLE HRMS.SALARY_COMPONENTS MODIFY (DEFAULT_VALUE NUMBER(20,10));
