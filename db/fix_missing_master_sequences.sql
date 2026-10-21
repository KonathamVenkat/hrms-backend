-- FIX: creates any sequence of the 16 new master tables that is missing in this database.
-- V5 defines all of them, but the dev DB was found without SEQ_RELIGIONS and SEQ_RELATION_TYPES. The backend checks
-- every sequence at startup (ddl-auto=validate) and refuses to start while one is missing, and the new masters
-- cannot insert rows without them. Run as the HRMS user, BEFORE starting the new backend build.
-- Safe to run twice: a sequence that exists is left alone. The definition is exactly the one in V5.
-- Undo: nothing to undo (an unused sequence is harmless); DROP SEQUENCE HRMS.<name> if you really want it gone.

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE ensure_sequence(p_name VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ALL_SEQUENCES WHERE SEQUENCE_OWNER = 'HRMS' AND SEQUENCE_NAME = p_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE 'CREATE SEQUENCE HRMS.' || p_name
                || ' INCREMENT BY 1 MINVALUE 1 MAXVALUE 999999999 NOCYCLE NOCACHE NOORDER';
            DBMS_OUTPUT.PUT_LINE('sequence created: ' || p_name);
        ELSE
            DBMS_OUTPUT.PUT_LINE('sequence exists: ' || p_name);
        END IF;
    END;
BEGIN
    ensure_sequence('SEQ_REGIONS');
    ensure_sequence('SEQ_WILAYAS');
    ensure_sequence('SEQ_PROVINCES');
    ensure_sequence('SEQ_CITIES');
    ensure_sequence('SEQ_RELIGIONS');
    ensure_sequence('SEQ_NATIONALITIES');
    ensure_sequence('SEQ_CONTRACT_TYPES');
    ensure_sequence('SEQ_PROFESSIONS');
    ensure_sequence('SEQ_EDUCATION_LEVELS');
    ensure_sequence('SEQ_LANGUAGES');
    ensure_sequence('SEQ_LANGUAGE_LEVELS');
    ensure_sequence('SEQ_HOUSE_TYPES');
    ensure_sequence('SEQ_RELATION_TYPES');
    ensure_sequence('SEQ_RELATIONS');
    ensure_sequence('SEQ_DIRECTORATES');
    ensure_sequence('SEQ_GRADES');
END;
/
