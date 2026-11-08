-- V26: hardening of the older master tables (code review of the admin masters, 2026-11-08).
-- 1. VERSION column on the seven older masters, so two admins can no longer overwrite each other silently.
-- 2. Their text columns, and those of DEPARTMENTS and DESIGNATIONS, count characters instead of bytes (what V6 did for the
--    newer tables): a 100-letter name with accents passed validation and then failed in Oracle (ORA-12899).
-- 3. Leave type codes become a real foreign key. LEAVE_REQUESTS only allowed 7 hard-coded codes and LEAVE_BALANCES 10, so
--    a request for HAJJ, BEREAVEMENT or STUDY (active types) was refused by the database, and a leave type added on the
--    admin screen could never be used. The two CHECK lists go; the foreign key to LEAVE_TYPES(CODE) replaces them and also
--    stops a code that is in use from being renamed.
-- 4. An index on every foreign key that lacked one (lookups, in-use checks and parent deletes scanned or locked the child).
-- 5. The column defaults of OFFICE_LOCATIONS were still Oman / Asia-Muscat; they are now South Sudan / Africa-Juba.
-- Not applied yet. Run by hand on HRMS with the backend stopped (Flyway is validate-only), then record it with
-- db/flyway_record_v26.sql. Rollback: db/rollback_V26.sql. Safe to run twice.
-- BEFORE running, look for leave rows whose code is not a leave type (the foreign key cannot be built while any exist):
--   SELECT DISTINCT LEAVE_TYPE FROM HRMS.LEAVE_REQUESTS WHERE LEAVE_TYPE NOT IN (SELECT CODE FROM HRMS.LEAVE_TYPES);
--   SELECT DISTINCT LEAVE_TYPE FROM HRMS.LEAVE_BALANCES WHERE LEAVE_TYPE NOT IN (SELECT CODE FROM HRMS.LEAVE_TYPES);

-- ===== 1. VERSION on the seven older masters =====
DECLARE
BEGIN
  FOR r IN (SELECT t.table_name
              FROM user_tables t
             WHERE t.table_name IN ('DOCUMENT_TYPES', 'HOLIDAY_CALENDAR', 'LEAVE_TYPES', 'OFFICE_LOCATIONS',
                                    'SALARY_COMPONENTS', 'SALARY_STRUCTURES', 'WORK_SHIFTS')
               AND NOT EXISTS (SELECT 1 FROM user_tab_columns c
                                WHERE c.table_name = t.table_name AND c.column_name = 'VERSION')) LOOP
    EXECUTE IMMEDIATE 'ALTER TABLE HRMS.' || r.table_name || ' ADD (VERSION NUMBER(10,0) DEFAULT 0 NOT NULL)';
  END LOOP;
END;
/

-- ===== 2. Character (not byte) length for the text columns of the older masters =====
DECLARE
BEGIN
  FOR r IN (SELECT table_name, column_name, char_length
              FROM user_tab_columns
             WHERE data_type = 'VARCHAR2'
               AND char_used = 'B'
               AND table_name IN ('DEPARTMENTS', 'DESIGNATIONS', 'DOCUMENT_TYPES', 'HOLIDAY_CALENDAR', 'LEAVE_TYPES',
                                  'OFFICE_LOCATIONS', 'SALARY_COMPONENTS', 'SALARY_STRUCTURES', 'WORK_SHIFTS')
             ORDER BY table_name, column_id) LOOP
    EXECUTE IMMEDIATE 'ALTER TABLE HRMS.' || r.table_name || ' MODIFY (' || r.column_name
                      || ' VARCHAR2(' || r.char_length || ' CHAR))';
  END LOOP;
END;
/

-- ===== 3. Leave type codes: drop the hard-coded lists, add the foreign keys =====
-- The list on LEAVE_REQUESTS has a system-generated name that differs per database, so it is found by its text.
DECLARE
BEGIN
  FOR r IN (SELECT table_name, constraint_name
              FROM user_constraints
             WHERE table_name IN ('LEAVE_REQUESTS', 'LEAVE_BALANCES')
               AND constraint_type = 'C'
               AND LOWER(search_condition_vc) LIKE '%leave_type%'
               AND LOWER(search_condition_vc) LIKE '% in (%') LOOP
    EXECUTE IMMEDIATE 'ALTER TABLE HRMS.' || r.table_name || ' DROP CONSTRAINT ' || r.constraint_name;
  END LOOP;
  FOR r IN (SELECT 'LEAVE_REQUESTS' AS table_name, 'FK_LR_LEAVE_TYPE' AS constraint_name FROM dual
            UNION ALL SELECT 'LEAVE_BALANCES', 'FK_LB_LEAVE_TYPE' FROM dual) LOOP
    DECLARE
      n NUMBER;
    BEGIN
      SELECT COUNT(*) INTO n FROM user_constraints WHERE constraint_name = r.constraint_name;
      IF n = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE HRMS.' || r.table_name || ' ADD CONSTRAINT ' || r.constraint_name
                          || ' FOREIGN KEY (LEAVE_TYPE) REFERENCES HRMS.LEAVE_TYPES (CODE)';
      END IF;
    END;
  END LOOP;
END;
/

-- ===== 4. An index on every foreign key that has none (named IX_<constraint>) =====
-- The index covers the foreign key columns in order. The tables of the other application that share the schema are left alone.
DECLARE
BEGIN
  FOR r IN (SELECT c.constraint_name, c.table_name,
                   LISTAGG(cc.column_name, ', ') WITHIN GROUP (ORDER BY cc.position) AS cols
              FROM user_constraints c
              JOIN user_cons_columns cc ON cc.constraint_name = c.constraint_name
             WHERE c.constraint_type = 'R'
               AND c.table_name NOT LIKE 'INVOICE%'
               AND NOT EXISTS (SELECT 1 FROM user_ind_columns i
                                WHERE i.table_name = c.table_name
                                  AND i.column_position = 1
                                  AND i.column_name = (SELECT x.column_name FROM user_cons_columns x
                                                        WHERE x.constraint_name = c.constraint_name AND x.position = 1))
             GROUP BY c.constraint_name, c.table_name) LOOP
    EXECUTE IMMEDIATE 'CREATE INDEX HRMS.IX_' || r.constraint_name || ' ON HRMS.' || r.table_name || ' (' || r.cols || ')';
  END LOOP;
END;
/

-- ===== 5. Office location defaults =====
ALTER TABLE HRMS.OFFICE_LOCATIONS MODIFY (COUNTRY DEFAULT 'South Sudan', TIMEZONE DEFAULT 'Africa/Juba');
