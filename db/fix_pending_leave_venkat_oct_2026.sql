-- One-off correction for a leave request submitted before the weekend moved to Saturday/Sunday.
--
-- konatham venkat (EMP-2026-1018) asked for Sick Leave on Sun 2026-10-04 to Mon 2026-10-05 on 2026-10-01.
-- Under the old Friday/Saturday weekend that was 2 working days, so TOTAL_DAYS = 2 and 2 days were put
-- into the balance's PENDING_DAYS. Under Saturday/Sunday only Monday counts: 1 day. Approval uses the stored
-- TOTAL_DAYS as it is (LeaveServiceImpl.processLeave), so it would take 2 days off his Sick Leave balance.
--
-- This sets the request to 1 day and takes 1 day off his Sick Leave PENDING_DAYS, in one block.
-- It only acts on a request that is still PENDING with TOTAL_DAYS = 2, so running it twice changes nothing
-- the second time. Run the whole block, check the two SELECTs at the end, then COMMIT (or ROLLBACK).
-- Approve the request in the app after the COMMIT.

SET SERVEROUTPUT ON

-- 1. Before.
SELECT LEAVE_REQ_ID, EMPLOYEE_CODE, LEAVE_TYPE, START_DATE, END_DATE, TOTAL_DAYS, STATUS
  FROM HRMS.LEAVE_REQUESTS
 WHERE EMPLOYEE_CODE = 'EMP-2026-1018' AND START_DATE = DATE '2026-10-04' AND END_DATE = DATE '2026-10-05';

SELECT b.EMPLOYEE_ID, b.LEAVE_TYPE, b.YEAR, b.TOTAL_DAYS, b.USED_DAYS, b.PENDING_DAYS
  FROM HRMS.LEAVE_BALANCES b
 WHERE b.EMPLOYEE_ID = (SELECT EMPLOYEE_ID FROM HRMS.EMPLOYEES WHERE EMPLOYEE_CODE = 'EMP-2026-1018')
   AND b.LEAVE_TYPE = 'SICK' AND b.YEAR = 2026;

-- 2. Correct the request and the balance together.
DECLARE
  v_emp_id HRMS.LEAVE_REQUESTS.EMPLOYEE_ID%TYPE;
BEGIN
  UPDATE HRMS.LEAVE_REQUESTS
     SET TOTAL_DAYS = 1
   WHERE EMPLOYEE_CODE = 'EMP-2026-1018'
     AND LEAVE_TYPE    = 'SICK'
     AND START_DATE    = DATE '2026-10-04'
     AND END_DATE      = DATE '2026-10-05'
     AND STATUS        = 'PENDING'
     AND TOTAL_DAYS    = 2
  RETURNING EMPLOYEE_ID INTO v_emp_id;

  IF SQL%ROWCOUNT <> 1 THEN
    DBMS_OUTPUT.PUT_LINE('Nothing changed: no PENDING 2-day request found (already fixed, or already processed).');
    RETURN;
  END IF;

  UPDATE HRMS.LEAVE_BALANCES
     SET PENDING_DAYS = GREATEST(0, PENDING_DAYS - 1)
   WHERE EMPLOYEE_ID = v_emp_id AND LEAVE_TYPE = 'SICK' AND YEAR = 2026;

  IF SQL%ROWCOUNT <> 1 THEN
    ROLLBACK;
    RAISE_APPLICATION_ERROR(-20001, 'Expected exactly one SICK 2026 balance row; request change rolled back.');
  END IF;

  DBMS_OUTPUT.PUT_LINE('Done: request set to 1 day, PENDING_DAYS reduced by 1. Check below, then COMMIT.');
END;
/

-- 3. After: the request should show TOTAL_DAYS = 1 and PENDING_DAYS should be 1 lower than before.
SELECT LEAVE_REQ_ID, TOTAL_DAYS, STATUS FROM HRMS.LEAVE_REQUESTS
 WHERE EMPLOYEE_CODE = 'EMP-2026-1018' AND START_DATE = DATE '2026-10-04' AND END_DATE = DATE '2026-10-05';

SELECT b.PENDING_DAYS, b.USED_DAYS FROM HRMS.LEAVE_BALANCES b
 WHERE b.EMPLOYEE_ID = (SELECT EMPLOYEE_ID FROM HRMS.EMPLOYEES WHERE EMPLOYEE_CODE = 'EMP-2026-1018')
   AND b.LEAVE_TYPE = 'SICK' AND b.YEAR = 2026;

-- COMMIT;
-- ROLLBACK;
