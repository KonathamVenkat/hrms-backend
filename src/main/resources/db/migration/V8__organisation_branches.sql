-- V8: organisation structure (workflow phase 1) -- a branch sits in an office location and has one manager,
-- and an employee's job row says which branch the employee belongs to.
-- Not applied yet. Run by hand on HRMS (Flyway is validate-only), then record it with db/flyway_record_v8.sql.
-- Rollback: db/rollback_V8.sql.
-- Renumbering note: the recruitment phase R2 script that was planned as V8 becomes V9 (interviews V10, offers V11).
-- Additive only: every new column is NULLABLE so existing rows keep working. HR fills them in through the
-- Organisation screens (set each branch's location and manager, then assign every employee a branch); a later
-- script can make EMPLOYEE_JOB_DETAILS.COMPANY_BRANCH_ID NOT NULL once the back-fill is done.
-- One manager per branch (the column holds one id); one person may manage several branches (no unique index on it).
-- Rules that need the code (the manager must be an active employee, the branch location must equal the job
-- location, a deactivated employee stops managing) live in the service layer.

-- ===== 1. A branch has a location and a manager =====
ALTER TABLE HRMS.COMPANY_BRANCHES ADD (
    LOCATION_ID                NUMBER(20,0),
    BRANCH_MANAGER_EMPLOYEE_ID NUMBER(20,0)
);
ALTER TABLE HRMS.COMPANY_BRANCHES ADD CONSTRAINT FK_CB_LOCATION
    FOREIGN KEY (LOCATION_ID) REFERENCES HRMS.OFFICE_LOCATIONS(LOCATION_ID);
ALTER TABLE HRMS.COMPANY_BRANCHES ADD CONSTRAINT FK_CB_MANAGER
    FOREIGN KEY (BRANCH_MANAGER_EMPLOYEE_ID) REFERENCES HRMS.EMPLOYEES(EMPLOYEE_ID);
CREATE INDEX IDX_CB_LOCATION_ID ON HRMS.COMPANY_BRANCHES (LOCATION_ID);
CREATE INDEX IDX_CB_MANAGER_ID  ON HRMS.COMPANY_BRANCHES (BRANCH_MANAGER_EMPLOYEE_ID);

-- ===== 2. An employee's job row names the branch =====
ALTER TABLE HRMS.EMPLOYEE_JOB_DETAILS ADD (COMPANY_BRANCH_ID NUMBER(20,0));
ALTER TABLE HRMS.EMPLOYEE_JOB_DETAILS ADD CONSTRAINT FK_EJD_BRANCH
    FOREIGN KEY (COMPANY_BRANCH_ID) REFERENCES HRMS.COMPANY_BRANCHES(COMPANY_BRANCH_ID);
CREATE INDEX IDX_EJD_BRANCH_ID ON HRMS.EMPLOYEE_JOB_DETAILS (COMPANY_BRANCH_ID);
