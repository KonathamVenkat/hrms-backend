-- ROLLBACK of V31__percentage_ten_decimals.sql. Stop the backend first. Run as the HRMS user, by hand.
-- Oracle cannot narrow a NUMBER column that holds data (ORA-01440), and the wider columns are harmless to the code
-- from before V31, so the columns are LEFT as they are. This puts the Armed Forces rate back to 3.33 (the code from
-- before V31 shows the extra decimals otherwise) and removes V31 from Flyway's history. Also remove the V31 file from
-- db/migration AND go back to the code from before it, or the app will not start. Safe to run twice.
UPDATE HRMS.SALARY_STRUCTURE_ITEMS i
   SET i.PERCENTAGE = ROUND(i.PERCENTAGE, 2), i.UPDATED_AT = SYSTIMESTAMP
 WHERE i.PERCENTAGE <> ROUND(i.PERCENTAGE, 2);
UPDATE HRMS.SALARY_COMPONENTS c
   SET c.DEFAULT_VALUE = ROUND(c.DEFAULT_VALUE, 2), c.UPDATED_AT = SYSTIMESTAMP
 WHERE c.DEFAULT_VALUE <> ROUND(c.DEFAULT_VALUE, 2);
DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '31';
COMMIT;
