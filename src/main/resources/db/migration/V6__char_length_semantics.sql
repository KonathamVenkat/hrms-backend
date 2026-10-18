-- V6: character (not byte) length for the text columns V5 created.
-- V5 left them in the database default, BYTE semantics: VARCHAR2(200) held 200 bytes, so a French or Arabic-script
-- name of ~100 letters passed the 200-character validation and then failed in Oracle (ORA-12899, shown as a 409).
-- Every VARCHAR2 column of the 27 new tables, and the 7 columns V5 added to EMPLOYEE_ADDRESSES and
-- EMPLOYEE_IDENTITY_INFO, becomes VARCHAR2(n CHAR) with the same n. Existing columns of older tables are untouched.
-- Safe to run twice: only columns still in BYTE semantics are changed. Run by hand, then record it with
-- db/flyway_record_v6.sql.
DECLARE
BEGIN
  FOR r IN (SELECT table_name, column_name, char_length
              FROM user_tab_columns
             WHERE data_type = 'VARCHAR2'
               AND char_used = 'B'
               AND (table_name IN ('REGIONS',
                           'RELIGIONS',
                           'NATIONALITIES',
                           'CONTRACT_TYPES',
                           'PROFESSIONS',
                           'EDUCATION_LEVELS',
                           'LANGUAGES',
                           'LANGUAGE_LEVELS',
                           'HOUSE_TYPES',
                           'RELATION_TYPES',
                           'BANKS',
                           'COMPANIES',
                           'WILAYAS',
                           'PROVINCES',
                           'CITIES',
                           'BANK_BRANCHES',
                           'COMPANY_BRANCHES',
                           'DIRECTORATES',
                           'GRADES',
                           'RELATIONS',
                           'EMPLOYEE_BANK_ACCOUNTS',
                           'EMPLOYEE_DEPENDENTS',
                           'EMPLOYEE_REFERENCES',
                           'EMPLOYEE_LANGUAGES',
                           'EMPLOYEE_EDUCATION',
                           'EMPLOYEE_TRAINING',
                           'EMPLOYEE_EXPERIENCE')
                    OR (table_name, column_name) IN (('EMPLOYEE_ADDRESSES', 'SQUARE'),
                           ('EMPLOYEE_ADDRESSES', 'HOUSE_NO'),
                           ('EMPLOYEE_ADDRESSES', 'HOUSE_OWNER_NAME'),
                           ('EMPLOYEE_ADDRESSES', 'HOUSE_OWNER_PHONE'),
                           ('EMPLOYEE_IDENTITY_INFO', 'NATIONAL_ID_TYPE'),
                           ('EMPLOYEE_IDENTITY_INFO', 'NATIONAL_ID_ISSUE_PLACE'),
                           ('EMPLOYEE_IDENTITY_INFO', 'RESIDENCE_PLACE')))
             ORDER BY table_name, column_id) LOOP
    EXECUTE IMMEDIATE 'ALTER TABLE HRMS.' || r.table_name || ' MODIFY (' || r.column_name
                      || ' VARCHAR2(' || r.char_length || ' CHAR))';
  END LOOP;
END;
/
