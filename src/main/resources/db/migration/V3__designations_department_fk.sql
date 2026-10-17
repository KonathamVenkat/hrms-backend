-- D-1: DESIGNATIONS.DEPARTMENT_ID must point at a real department.
-- V1__baseline.sql never created this foreign key (dev has it under the name FK_DESIG_DEPARTMENT_ID, added by
-- hand), so a department could be deleted while a designation was being created under it.
-- Run as the HRMS schema owner. Safe to run twice and on dev, where the key already exists.
-- It stops with an error, changing nothing, if a designation points at a department that does not exist:
--   SELECT d.DESIG_ID, d.CODE, d.DEPARTMENT_ID FROM HRMS.DESIGNATIONS d
--    WHERE d.DEPARTMENT_ID IS NOT NULL
--      AND NOT EXISTS (SELECT 1 FROM HRMS.DEPARTMENTS x WHERE x.DEPT_ID = d.DEPARTMENT_ID);
-- Fix those rows (set DEPARTMENT_ID to NULL or a valid id), then run this again.
DECLARE
    v_orphans NUMBER;
    v_fk      NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_fk
      FROM ALL_CONSTRAINTS c JOIN ALL_CONS_COLUMNS cc
        ON cc.OWNER = c.OWNER AND cc.CONSTRAINT_NAME = c.CONSTRAINT_NAME
     WHERE c.OWNER = 'HRMS' AND c.TABLE_NAME = 'DESIGNATIONS'
       AND c.CONSTRAINT_TYPE = 'R' AND cc.COLUMN_NAME = 'DEPARTMENT_ID';
    IF v_fk > 0 THEN
        RETURN;
    END IF;

    SELECT COUNT(*) INTO v_orphans
      FROM HRMS.DESIGNATIONS d
     WHERE d.DEPARTMENT_ID IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM HRMS.DEPARTMENTS x WHERE x.DEPT_ID = d.DEPARTMENT_ID);
    IF v_orphans > 0 THEN
        RAISE_APPLICATION_ERROR(-20001, v_orphans || ' designation(s) point at a missing department; fix them first.');
    END IF;

    EXECUTE IMMEDIATE 'ALTER TABLE HRMS.DESIGNATIONS ADD CONSTRAINT FK_DESIG_DEPARTMENT_ID '
        || 'FOREIGN KEY (DEPARTMENT_ID) REFERENCES HRMS.DEPARTMENTS (DEPT_ID)';
END;
/
