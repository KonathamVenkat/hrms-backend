-- V15: fourth name (the family name on the paper basic-data forms) for employees, their dependents (spouse, children, parents,
-- next of kin) and reference persons. Not applied yet. Run by hand on HRMS with the backend stopped (Flyway is validate-only),
-- then record it with db/flyway_record_v15.sql. Rollback: db/rollback_V15.sql.
-- Additive and nullable: every existing row stays valid. English is VARCHAR2 with CHAR semantics, Arabic NVARCHAR2.
ALTER TABLE HRMS.EMPLOYEES ADD (FOURTH_NAME VARCHAR2(100 CHAR), FOURTH_NAME_AR NVARCHAR2(200));
ALTER TABLE HRMS.EMPLOYEE_DEPENDENTS ADD (FOURTH_NAME VARCHAR2(100 CHAR), FOURTH_NAME_AR NVARCHAR2(200));
ALTER TABLE HRMS.EMPLOYEE_REFERENCES ADD (FOURTH_NAME VARCHAR2(100 CHAR), FOURTH_NAME_AR NVARCHAR2(200));
