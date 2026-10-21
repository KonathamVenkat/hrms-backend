-- V9: language levels get a sort order, so dropdowns list them by proficiency (Basic ... Native) instead of A-Z.
-- Not applied yet. Run by hand on HRMS (Flyway is validate-only), then record it with db/flyway_record_v9.sql.
-- Rollback: db/rollback_V9.sql. Additive: the column is NULLABLE; rows without a value are listed last, by name.
-- Renumbering note: the recruitment phase R2 script planned as V9 becomes V10 (interviews V11, offers V12).
ALTER TABLE HRMS.LANGUAGE_LEVELS ADD (SORT_ORDER NUMBER(4,0));

UPDATE HRMS.LANGUAGE_LEVELS SET SORT_ORDER = 10 WHERE LANGUAGE_LEVELS_CODE = 'BASIC';
UPDATE HRMS.LANGUAGE_LEVELS SET SORT_ORDER = 20 WHERE LANGUAGE_LEVELS_CODE = 'INTERMEDIATE';
UPDATE HRMS.LANGUAGE_LEVELS SET SORT_ORDER = 30 WHERE LANGUAGE_LEVELS_CODE = 'ADVANCED';
UPDATE HRMS.LANGUAGE_LEVELS SET SORT_ORDER = 40 WHERE LANGUAGE_LEVELS_CODE = 'FLUENT';
UPDATE HRMS.LANGUAGE_LEVELS SET SORT_ORDER = 50 WHERE LANGUAGE_LEVELS_CODE = 'NATIVE';
