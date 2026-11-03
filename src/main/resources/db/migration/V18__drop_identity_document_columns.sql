-- V18: removes the five "second identity document" columns that V17 added to EMPLOYEE_IDENTITY_INFO. The Upload Document dialog on
-- the employee already records document type, number, issuer, issue date and expiry date, so these columns duplicated it.
-- RESIDENCE_VALID_UNTIL (also from V17) stays. Not applied yet. Run by hand on HRMS with the backend stopped (Flyway is
-- validate-only), then record it with db/flyway_record_v18.sql. Rollback: db/rollback_V18.sql.
-- Whatever was typed into these five columns is lost; the screen never offered them, so dev is expected to hold no values.
ALTER TABLE HRMS.EMPLOYEE_IDENTITY_INFO DROP (DOCUMENT_TYPE, DOCUMENT_NO, DOCUMENT_ISSUE_DATE, DOCUMENT_ISSUE_PLACE, DOCUMENT_VALID_UNTIL);
