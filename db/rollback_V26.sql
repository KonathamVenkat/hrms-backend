-- ROLLBACK of V26__masters_hardening.sql. Stop the backend first. Run as the HRMS user, by hand. Then:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '26';  COMMIT;
-- and remove the migration file AND go back to the code from before V26 (the entities of the seven older masters map
-- VERSION, the leave services expect the foreign keys), or the app will not start.
-- Left as they are on purpose: the character-length text columns (harmless, and not worth reversing).

-- 1. The indexes V26 added on foreign keys (named IX_ + the constraint name).
DECLARE
  n NUMBER;
BEGIN
  FOR r IN (SELECT constraint_name FROM user_constraints WHERE constraint_type = 'R') LOOP
    SELECT COUNT(*) INTO n FROM user_indexes WHERE index_name = 'IX_' || r.constraint_name;
    IF n > 0 THEN
      EXECUTE IMMEDIATE 'DROP INDEX HRMS.IX_' || r.constraint_name;
    END IF;
  END LOOP;
END;
/

-- 2. The leave type foreign keys.
ALTER TABLE HRMS.LEAVE_REQUESTS DROP CONSTRAINT FK_LR_LEAVE_TYPE;
ALTER TABLE HRMS.LEAVE_BALANCES DROP CONSTRAINT FK_LB_LEAVE_TYPE;

-- 3. The hard-coded code lists are NOT restored here: LEAVE_REQUESTS would refuse HAJJ, BEREAVEMENT and STUDY again and
--    any leave type added since. To restore them anyway, first make sure no row uses another code, then:
--   ALTER TABLE HRMS.LEAVE_REQUESTS ADD CHECK (LEAVE_TYPE IN ('ANNUAL','SICK','CASUAL','MATERNITY','PATERNITY','COMP_OFF','UNPAID'));
--   ALTER TABLE HRMS.LEAVE_BALANCES ADD CONSTRAINT CHK_LB_LEAVE_TYPE CHECK (LEAVE_TYPE IN ('ANNUAL','SICK','CASUAL',
--     'MATERNITY','PATERNITY','COMP_OFF','UNPAID','HAJJ','BEREAVEMENT','STUDY'));

-- 4. The VERSION columns of the seven older masters.
ALTER TABLE HRMS.DOCUMENT_TYPES DROP COLUMN VERSION;
ALTER TABLE HRMS.HOLIDAY_CALENDAR DROP COLUMN VERSION;
ALTER TABLE HRMS.LEAVE_TYPES DROP COLUMN VERSION;
ALTER TABLE HRMS.OFFICE_LOCATIONS DROP COLUMN VERSION;
ALTER TABLE HRMS.SALARY_COMPONENTS DROP COLUMN VERSION;
ALTER TABLE HRMS.SALARY_STRUCTURES DROP COLUMN VERSION;
ALTER TABLE HRMS.WORK_SHIFTS DROP COLUMN VERSION;

-- 5. The office location defaults.
ALTER TABLE HRMS.OFFICE_LOCATIONS MODIFY (COUNTRY DEFAULT 'Oman', TIMEZONE DEFAULT 'Asia/Muscat');
