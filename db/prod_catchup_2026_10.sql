-- Prod catch-up, October 2026: brings an existing HRMS schema up to what the backend now expects.
-- Hibernate runs with ddl-auto=validate, so this MUST be applied BEFORE deploying the matching
-- backend build, or the application will not start.
--
-- It covers every structural script in db/ since 2026-09-29 plus the audit-trail menu, and it
-- checks each piece before touching it, so it is SAFE TO RUN ON A SCHEMA THAT HAS SOME OF THEM:
--   auth_must_change_password, leave_request_attachment, store_documents_in_db, employee_photo,
--   optimistic_locking_and_audit (both versions of that file), rename_pasi_to_nssf, audit_events,
--   audit_trail_menu, work_shifts_sat_sun_weekend (data).
-- It does NOT run: hide_unbuilt_menu_items.sql, employee_menu_profile.sql (menu data that a
-- re-run could undo), or any cleanup_/fix_ script (development test data).
--
-- HOW TO USE
--   1. Take the backup from docs/DEPLOY.md step 0 first.
--   2. Run this file as it is: a DRY RUN. It prints OK or MISSING per item and changes nothing.
--   3. Read the output. Then change v_apply to TRUE below and run it again to apply.
--   4. Run it a third time (dry run again): every line must say OK and "0 change(s) missing".
-- Needs a user that can ALTER/CREATE in HRMS and read ALL_TAB_COLUMNS (the schema owner or a DBA).
-- Run with Run Script (F5) in SQL Developer, or in SQL*Plus.

SET SERVEROUTPUT ON SIZE UNLIMITED

DECLARE
    v_apply   CONSTANT BOOLEAN := FALSE;   -- <<< change to TRUE to apply
    v_missing PLS_INTEGER := 0;
    n         PLS_INTEGER;

    FUNCTION has_table(p_table VARCHAR2) RETURN BOOLEAN IS
        n PLS_INTEGER;
    BEGIN
        SELECT COUNT(*) INTO n FROM ALL_TABLES WHERE OWNER = 'HRMS' AND TABLE_NAME = p_table;
        RETURN n > 0;
    END;

    FUNCTION has_col(p_table VARCHAR2, p_col VARCHAR2) RETURN BOOLEAN IS
        n PLS_INTEGER;
    BEGIN
        SELECT COUNT(*) INTO n FROM ALL_TAB_COLUMNS
         WHERE OWNER = 'HRMS' AND TABLE_NAME = p_table AND COLUMN_NAME = p_col;
        RETURN n > 0;
    END;

    FUNCTION has_index(p_index VARCHAR2) RETURN BOOLEAN IS
        n PLS_INTEGER;
    BEGIN
        SELECT COUNT(*) INTO n FROM ALL_INDEXES WHERE OWNER = 'HRMS' AND INDEX_NAME = p_index;
        RETURN n > 0;
    END;

    -- Reports one item; when applying, runs p_sql.
    PROCEDURE act(p_label VARCHAR2, p_sql VARCHAR2) IS
    BEGIN
        v_missing := v_missing + 1;
        IF v_apply THEN
            EXECUTE IMMEDIATE p_sql;
            DBMS_OUTPUT.PUT_LINE('APPLIED  ' || p_label);
        ELSE
            DBMS_OUTPUT.PUT_LINE('MISSING  ' || p_label);
        END IF;
    END;

    PROCEDURE ok(p_label VARCHAR2) IS
    BEGIN
        DBMS_OUTPUT.PUT_LINE('OK       ' || p_label);
    END;

    PROCEDURE add_col(p_table VARCHAR2, p_col VARCHAR2, p_def VARCHAR2) IS
    BEGIN
        IF NOT has_table(p_table) THEN
            DBMS_OUTPUT.PUT_LINE('ERROR    table HRMS.' || p_table || ' does not exist: this is not the schema this script expects');
            v_missing := v_missing + 1;
        ELSIF has_col(p_table, p_col) THEN
            ok(p_table || '.' || p_col);
        ELSE
            act(p_table || '.' || p_col, 'ALTER TABLE HRMS.' || p_table || ' ADD (' || p_col || ' ' || p_def || ')');
        END IF;
    END;

    PROCEDURE add_table(p_table VARCHAR2, p_sql VARCHAR2) IS
    BEGIN
        IF has_table(p_table) THEN ok('table ' || p_table); ELSE act('table ' || p_table, p_sql); END IF;
    END;

    PROCEDURE add_index(p_index VARCHAR2, p_sql VARCHAR2) IS
    BEGIN
        IF has_index(p_index) THEN ok('index ' || p_index); ELSE act('index ' || p_index, p_sql); END IF;
    END;

BEGIN
    DBMS_OUTPUT.PUT_LINE('--- ' || CASE WHEN v_apply THEN 'APPLY MODE' ELSE 'DRY RUN (nothing is changed)' END || ' ---');

    -- auth_must_change_password.sql
    add_col('AUTH_USERS', 'MUST_CHANGE_PASSWORD', 'NUMBER(1) DEFAULT 0 NOT NULL');

    -- leave_request_attachment.sql
    add_col('LEAVE_REQUESTS', 'ATTACHMENT_NAME', 'VARCHAR2(255)');
    add_col('LEAVE_REQUESTS', 'ATTACHMENT_SIZE', 'NUMBER(12)');
    add_col('LEAVE_TYPES', 'DOC_MAX_FILE_SIZE_MB', 'NUMBER(3)');
    add_col('LEAVE_TYPES', 'DOC_ALLOWED_EXTENSIONS', 'VARCHAR2(100)');

    -- store_documents_in_db.sql
    add_table('EMPLOYEE_DOCUMENT_CONTENT',
        'CREATE TABLE HRMS.EMPLOYEE_DOCUMENT_CONTENT (' ||
        ' DOCUMENT_ID NUMBER NOT NULL, CONTENT BLOB NOT NULL,' ||
        ' CONSTRAINT PK_EMP_DOC_CONTENT PRIMARY KEY (DOCUMENT_ID),' ||
        ' CONSTRAINT FK_EMP_DOC_CONTENT_DOC FOREIGN KEY (DOCUMENT_ID) REFERENCES HRMS.EMPLOYEE_DOCUMENTS (DOCUMENT_ID))');
    add_table('LEAVE_REQUEST_ATTACHMENT_CONTENT',
        'CREATE TABLE HRMS.LEAVE_REQUEST_ATTACHMENT_CONTENT (' ||
        ' LEAVE_REQ_ID NUMBER NOT NULL, CONTENT BLOB NOT NULL,' ||
        ' CONSTRAINT PK_LEAVE_ATT_CONTENT PRIMARY KEY (LEAVE_REQ_ID),' ||
        ' CONSTRAINT FK_LEAVE_ATT_CONTENT_REQ FOREIGN KEY (LEAVE_REQ_ID) REFERENCES HRMS.LEAVE_REQUESTS (LEAVE_REQ_ID))');
    SELECT COUNT(*) INTO n FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'HRMS' AND TABLE_NAME = 'EMPLOYEE_DOCUMENTS' AND COLUMN_NAME = 'FILE_PATH' AND NULLABLE = 'N';
    IF n = 0 THEN ok('EMPLOYEE_DOCUMENTS.FILE_PATH is nullable');
    ELSE act('EMPLOYEE_DOCUMENTS.FILE_PATH nullable', 'ALTER TABLE HRMS.EMPLOYEE_DOCUMENTS MODIFY (FILE_PATH NULL)');
    END IF;

    -- employee_photo.sql
    add_table('EMPLOYEE_PHOTO',
        'CREATE TABLE HRMS.EMPLOYEE_PHOTO (' ||
        ' EMPLOYEE_ID NUMBER NOT NULL, CONTENT BLOB NOT NULL, CONTENT_TYPE VARCHAR2(50) NOT NULL,' ||
        ' UPDATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,' ||
        ' CONSTRAINT PK_EMPLOYEE_PHOTO PRIMARY KEY (EMPLOYEE_ID),' ||
        ' CONSTRAINT FK_EMPLOYEE_PHOTO_EMP FOREIGN KEY (EMPLOYEE_ID) REFERENCES HRMS.EMPLOYEES (EMPLOYEE_ID))');

    -- optimistic_locking_and_audit.sql (the 2026-10-05 part, then the 2026-10-06 additions)
    add_col('EMPLOYEES',              'VERSION',    'NUMBER(10) DEFAULT 0 NOT NULL');
    add_col('EMPLOYEE_ADDRESSES',     'VERSION',    'NUMBER(10) DEFAULT 0 NOT NULL');
    add_col('EMPLOYEE_IDENTITY_INFO', 'VERSION',    'NUMBER(10) DEFAULT 0 NOT NULL');
    add_col('LEAVE_BALANCES',         'VERSION',    'NUMBER(10) DEFAULT 0 NOT NULL');
    add_col('EMPLOYEE_ADDRESSES',     'CREATED_BY', 'VARCHAR2(36)');
    add_col('EMPLOYEE_ADDRESSES',     'UPDATED_BY', 'VARCHAR2(36)');
    add_col('EMPLOYEE_IDENTITY_INFO', 'CREATED_BY', 'VARCHAR2(36)');
    add_col('EMPLOYEE_IDENTITY_INFO', 'UPDATED_BY', 'VARCHAR2(36)');
    add_col('LEAVE_REQUESTS',            'VERSION', 'NUMBER(10) DEFAULT 0 NOT NULL');
    add_col('OVERTIME_REQUESTS',         'VERSION', 'NUMBER(10) DEFAULT 0 NOT NULL');
    add_col('ATTENDANCE_REGULARIZATION', 'VERSION', 'NUMBER(10) DEFAULT 0 NOT NULL');

    -- rename_pasi_to_nssf.sql
    IF has_col('SALARY_COMPONENTS', 'IS_NSSF_APPLICABLE') THEN
        ok('SALARY_COMPONENTS.IS_NSSF_APPLICABLE');
    ELSIF has_col('SALARY_COMPONENTS', 'IS_PASI_APPLICABLE') THEN
        act('SALARY_COMPONENTS rename IS_PASI_APPLICABLE to IS_NSSF_APPLICABLE',
            'ALTER TABLE HRMS.SALARY_COMPONENTS RENAME COLUMN IS_PASI_APPLICABLE TO IS_NSSF_APPLICABLE');
    ELSE
        DBMS_OUTPUT.PUT_LINE('ERROR    SALARY_COMPONENTS has neither IS_PASI_APPLICABLE nor IS_NSSF_APPLICABLE');
        v_missing := v_missing + 1;
    END IF;

    -- audit_events.sql
    add_table('AUDIT_EVENTS',
        'CREATE TABLE HRMS.AUDIT_EVENTS (' ||
        ' EVENT_ID NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,' ||
        ' EVENT_TIME TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,' ||
        ' ACTOR VARCHAR2(100) NOT NULL, ACTION VARCHAR2(60) NOT NULL,' ||
        ' TARGET_TYPE VARCHAR2(40), TARGET_ID VARCHAR2(64), DETAIL VARCHAR2(1000))');
    IF has_table('AUDIT_EVENTS') OR v_apply THEN
        add_index('IDX_AUDIT_TARGET', 'CREATE INDEX HRMS.IDX_AUDIT_TARGET ON HRMS.AUDIT_EVENTS (TARGET_TYPE, TARGET_ID)');
        add_index('IDX_AUDIT_ACTOR',  'CREATE INDEX HRMS.IDX_AUDIT_ACTOR ON HRMS.AUDIT_EVENTS (ACTOR, EVENT_TIME)');
        add_index('IDX_AUDIT_TIME',   'CREATE INDEX HRMS.IDX_AUDIT_TIME ON HRMS.AUDIT_EVENTS (EVENT_TIME)');
    END IF;

    -- audit_trail_menu.sql: Admin Config > Audit Trail, HR_ADMIN, view only
    SELECT COUNT(*) INTO n FROM HRMS.SUB_MENU WHERE SUB_MENU_CODE = 'ADMIN_AUDIT';
    IF n > 0 THEN ok('menu ADMIN_AUDIT');
    ELSE
        act('menu ADMIN_AUDIT (Admin Config > Audit Trail)',
            'INSERT INTO HRMS.SUB_MENU (SUB_MENU_CODE, SUB_MENU_NAME, SUB_MENU_ACTION, SUB_MENU_TYPE, MAIN_MENU_ID, SORT_ORDER, IS_ACTIVE) ' ||
            'SELECT ''ADMIN_AUDIT'', ''Audit Trail'', ''/app/admin/audit-events'', 1, MAIN_MENU_ID, 6, 1 ' ||
            'FROM HRMS.MAIN_MENU WHERE MAIN_MENU_NAME = ''Admin Config''');
    END IF;
    SELECT COUNT(*) INTO n FROM HRMS.ROLE_RIGHTS rr JOIN HRMS.SUB_MENU s ON s.SUB_MENU_ID = rr.MENU_ID
     WHERE s.SUB_MENU_CODE = 'ADMIN_AUDIT';
    IF n > 0 THEN ok('menu ADMIN_AUDIT rights');
    ELSE
        act('menu ADMIN_AUDIT rights (HR_ADMIN, view only)',
            'INSERT INTO HRMS.ROLE_RIGHTS (ROLE_ID, MAIN_MENU_ID, MENU_ID, CAN_VIEW, CAN_CREATE, CAN_EDIT, CAN_DELETE) ' ||
            'SELECT r.ROLE_ID, s.MAIN_MENU_ID, s.SUB_MENU_ID, 1, 0, 0, 0 ' ||
            'FROM HRMS.ROLES r, HRMS.SUB_MENU s WHERE r.ROLE_NAME = ''HR_ADMIN'' AND s.SUB_MENU_CODE = ''ADMIN_AUDIT''');
    END IF;

    -- work_shifts_sat_sun_weekend.sql (data): leave and attendance now count days by the shift's
    -- WORKING_DAYS, and the weekend is Saturday and Sunday. A shift that still lists the old
    -- SUN-THU week would charge Saturday and give Friday free. Only the exact old value is changed;
    -- a shift you set up by hand is left alone.
    SELECT COUNT(*) INTO n FROM HRMS.WORK_SHIFTS
     WHERE REPLACE(UPPER(WORKING_DAYS), ' ', '') = 'SUN,MON,TUE,WED,THU';
    IF n = 0 THEN ok('no work shift still lists the old SUN-THU week');
    ELSE
        act(n || ' work shift(s) still list the old SUN-THU week -> MON-FRI',
            'UPDATE HRMS.WORK_SHIFTS SET WORKING_DAYS = ''MON,TUE,WED,THU,FRI'' ' ||
            'WHERE REPLACE(UPPER(WORKING_DAYS), '' '', '''') = ''SUN,MON,TUE,WED,THU''');
    END IF;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('--- ' || v_missing || ' change(s) ' || CASE WHEN v_apply THEN 'applied' ELSE 'missing' END || ' ---');
    IF NOT v_apply AND v_missing > 0 THEN
        DBMS_OUTPUT.PUT_LINE('Dry run only. To apply, set v_apply to TRUE at the top and run again.');
    END IF;
END;
/

-- Shifts as they are now, so you can see that Saturday and Sunday are not working days:
SELECT SHIFT_CODE, SHIFT_NAME, WORKING_DAYS, IS_ACTIVE FROM HRMS.WORK_SHIFTS ORDER BY SHIFT_CODE;
