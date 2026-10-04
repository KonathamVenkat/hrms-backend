-- Employee profile photos are stored in the database.
-- Hibernate runs with ddl-auto=validate, so this MUST be applied to the HRMS schema
-- BEFORE deploying the matching backend build, or the app will fail to start.

-- One row per employee that has an uploaded photo. Kept apart from HRMS.EMPLOYEES so listing and
-- editing an employee never reads the BLOB.
CREATE TABLE HRMS.EMPLOYEE_PHOTO (
    EMPLOYEE_ID   NUMBER         NOT NULL,
    CONTENT       BLOB           NOT NULL,
    CONTENT_TYPE  VARCHAR2(50)   NOT NULL,
    UPDATED_AT    TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_EMPLOYEE_PHOTO PRIMARY KEY (EMPLOYEE_ID),
    CONSTRAINT FK_EMPLOYEE_PHOTO_EMP FOREIGN KEY (EMPLOYEE_ID)
        REFERENCES HRMS.EMPLOYEES (EMPLOYEE_ID)
);
