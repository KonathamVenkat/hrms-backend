-- V27: a master name is unique (ignoring upper/lower case), like its code already was.
-- Flat masters (regions, religions, nationalities, contract types, professions, education levels, languages, language
-- levels, house types, relation types, banks, companies, specializations, grades) and departments: unique overall.
-- Masters under a parent: unique inside the parent (a wilaya name per region, a province per wilaya, a city per province,
-- a bank branch per bank, a company branch per company, a directorate per company branch, a relation per relation type,
-- a section per department, a designation title per department; designations without a department count as one group).
-- The services check the same rule first and answer with a clear duplicate message; these indexes are the backstop for
-- two people saving at the same moment.
-- Not applied yet. Run by hand on HRMS with the backend stopped (Flyway is validate-only), then record it with
-- db/flyway_record_v27.sql. Rollback: db/rollback_V27.sql. Safe to run twice (an index that exists is skipped).
-- BEFORE running, look for names that already repeat; an index cannot be built while any exist, and the script stops at the
-- first one (ORA-01452). Checked on the dev database on 2026-11-08: none. Example for one master:
--   SELECT UPPER(TRIM(TITLE)), NVL(DEPARTMENT_ID, 0), COUNT(*) FROM HRMS.DESIGNATIONS
--    GROUP BY UPPER(TRIM(TITLE)), NVL(DEPARTMENT_ID, 0) HAVING COUNT(*) > 1;
-- (the clean-up script db/masters_data_cleanup_2026_11.sql removes the six duplicate designation titles that existed in
-- different departments; those are allowed by this rule, so it does not matter which script runs first)

DECLARE
  PROCEDURE make(p_index VARCHAR2, p_table VARCHAR2, p_columns VARCHAR2) IS
    n NUMBER;
  BEGIN
    SELECT COUNT(*) INTO n FROM user_indexes WHERE index_name = p_index;
    IF n = 0 THEN
      EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX HRMS.' || p_index || ' ON HRMS.' || p_table || ' (' || p_columns || ')';
    END IF;
  END;
BEGIN
  -- unique overall
  make('UQ_REGIONS_NAME',          'REGIONS',          'UPPER(REGIONS_NAME)');
  make('UQ_RELIGIONS_NAME',        'RELIGIONS',        'UPPER(RELIGIONS_NAME)');
  make('UQ_NATIONALITIES_NAME',    'NATIONALITIES',    'UPPER(NATIONALITIES_NAME)');
  make('UQ_CONTRACT_TYPES_NAME',   'CONTRACT_TYPES',   'UPPER(CONTRACT_TYPES_NAME)');
  make('UQ_PROFESSIONS_NAME',      'PROFESSIONS',      'UPPER(PROFESSIONS_NAME)');
  make('UQ_EDUCATION_LEVELS_NAME', 'EDUCATION_LEVELS', 'UPPER(EDUCATION_LEVELS_NAME)');
  make('UQ_LANGUAGES_NAME',        'LANGUAGES',        'UPPER(LANGUAGES_NAME)');
  make('UQ_LANGUAGE_LEVELS_NAME',  'LANGUAGE_LEVELS',  'UPPER(LANGUAGE_LEVELS_NAME)');
  make('UQ_HOUSE_TYPES_NAME',      'HOUSE_TYPES',      'UPPER(HOUSE_TYPES_NAME)');
  make('UQ_RELATION_TYPES_NAME',   'RELATION_TYPES',   'UPPER(RELATION_TYPES_NAME)');
  make('UQ_BANKS_NAME',            'BANKS',            'UPPER(BANKS_NAME)');
  make('UQ_COMPANIES_NAME',        'COMPANIES',        'UPPER(COMPANIES_NAME)');
  make('UQ_SPECIALIZATIONS_NAME',  'SPECIALIZATIONS',  'UPPER(SPECIALIZATIONS_NAME)');
  make('UQ_GRADES_NAME',           'GRADES',           'UPPER(GRADES_NAME)');
  make('UQ_DEPARTMENTS_NAME',      'DEPARTMENTS',      'UPPER(NAME)');
  -- unique inside the parent
  make('UQ_WILAYAS_NAME',          'WILAYAS',          'REGION_ID, UPPER(WILAYAS_NAME)');
  make('UQ_PROVINCES_NAME',        'PROVINCES',        'WILAYA_ID, UPPER(PROVINCES_NAME)');
  make('UQ_CITIES_NAME',           'CITIES',           'PROVINCE_ID, UPPER(CITIES_NAME)');
  make('UQ_BANK_BRANCHES_NAME',    'BANK_BRANCHES',    'BANK_ID, UPPER(BANK_BRANCHES_NAME)');
  make('UQ_COMPANY_BRANCHES_NAME', 'COMPANY_BRANCHES', 'COMPANY_ID, UPPER(COMPANY_BRANCHES_NAME)');
  make('UQ_DIRECTORATES_NAME',     'DIRECTORATES',     'COMPANY_BRANCH_ID, UPPER(DIRECTORATES_NAME)');
  make('UQ_RELATIONS_NAME',        'RELATIONS',        'RELATION_TYPE_ID, UPPER(RELATIONS_NAME)');
  make('UQ_SECTIONS_NAME',         'SECTIONS',         'DEPT_ID, UPPER(SECTION_NAME)');
  make('UQ_DESIGNATIONS_NAME',     'DESIGNATIONS',     'NVL(DEPARTMENT_ID, 0), UPPER(TITLE)');
END;
/
