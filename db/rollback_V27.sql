-- ROLLBACK of V27__unique_master_names.sql. Stop the backend first. Run as the HRMS user, by hand. Then:
--   DELETE FROM HRMS."flyway_schema_history" WHERE "version" = '27';  COMMIT;
-- and remove the migration file AND go back to the code from before V27 (the services would still check the name and
-- answer with the duplicate message), or the app will not start.
DECLARE
BEGIN
  FOR r IN (SELECT index_name FROM user_indexes
             WHERE index_name IN ('UQ_REGIONS_NAME', 'UQ_RELIGIONS_NAME', 'UQ_NATIONALITIES_NAME', 'UQ_CONTRACT_TYPES_NAME',
                                  'UQ_PROFESSIONS_NAME', 'UQ_EDUCATION_LEVELS_NAME', 'UQ_LANGUAGES_NAME',
                                  'UQ_LANGUAGE_LEVELS_NAME', 'UQ_HOUSE_TYPES_NAME', 'UQ_RELATION_TYPES_NAME', 'UQ_BANKS_NAME',
                                  'UQ_COMPANIES_NAME', 'UQ_SPECIALIZATIONS_NAME', 'UQ_GRADES_NAME', 'UQ_DEPARTMENTS_NAME',
                                  'UQ_WILAYAS_NAME', 'UQ_PROVINCES_NAME', 'UQ_CITIES_NAME', 'UQ_BANK_BRANCHES_NAME',
                                  'UQ_COMPANY_BRANCHES_NAME', 'UQ_DIRECTORATES_NAME', 'UQ_RELATIONS_NAME', 'UQ_SECTIONS_NAME',
                                  'UQ_DESIGNATIONS_NAME')) LOOP
    EXECUTE IMMEDIATE 'DROP INDEX HRMS.' || r.index_name;
  END LOOP;
END;
/
