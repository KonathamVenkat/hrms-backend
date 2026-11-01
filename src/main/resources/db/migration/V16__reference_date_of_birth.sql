-- V16: date of birth of a reference person (field on the paper basic-data form). Run by hand on HRMS with the backend stopped
-- (Flyway is validate-only), then record it with db/flyway_record_v16.sql. Rollback: db/rollback_V16.sql.
-- Additive and nullable. Safe where the column already exists (dev has it): that case is skipped, so on such a database you
-- only need db/flyway_record_v16.sql. The migration file stays so a fresh or test database gets the column too.
BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE HRMS.EMPLOYEE_REFERENCES ADD (DATE_OF_BIRTH DATE)';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -1430 THEN RAISE; END IF;   -- ORA-01430: column being added already exists in table
END;
/
