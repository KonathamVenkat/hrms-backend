-- Removes the test data left by the weekend / approved-overtime / approved-leave check-in tests.
--
-- konatham Shivansh (EMP-2026-1019, EMPLOYEE_ID 1019) was used for all three tests:
--   * LEAVE_REQUESTS id 26   : approved Casual Leave on Mon 2026-10-05 (leave check-in block test, passed)
--       -> deleted; his CASUAL balance USED_DAYS goes back from 1 to 0
--   * OVERTIME_REQUESTS OT-2026-000003 : approved Pre-Approved OT on Sun 2026-10-04 (weekend check-in test, passed)
--       -> deleted
--   * ATTENDANCE_LOGS id 2367: the Sun 2026-10-04 check-in made with that overtime
--       -> deleted (plus any regularization / leave attachment rows pointing at the deleted rows)
--
-- Each step only acts on exactly the row described, so running the block twice changes nothing the second time.
-- Run the whole block, check the SELECTs at the end, then COMMIT (or ROLLBACK).
-- AFTER the COMMIT, re-run the attendance back-fill for 2026-10-04 so that Sunday is a WEEKEND row again and the
-- October summary is recalculated:
--   POST /api/v1/attendance/admin/day-records?from=2026-10-04&to=2026-10-04   (as HR_ADMIN)
-- (The 00:05 nightly job would also recreate the row, but would not refresh the summary until it re-checks.)

SET SERVEROUTPUT ON

-- 1. Before.
SELECT LEAVE_REQ_ID, EMPLOYEE_CODE, LEAVE_TYPE, START_DATE, TOTAL_DAYS, STATUS
  FROM HRMS.LEAVE_REQUESTS WHERE LEAVE_REQ_ID = 26;
SELECT BALANCE_ID, LEAVE_TYPE, TOTAL_DAYS, USED_DAYS, PENDING_DAYS
  FROM HRMS.LEAVE_BALANCES WHERE EMPLOYEE_ID = 1019 AND YEAR = 2026 AND LEAVE_TYPE = 'CASUAL';
SELECT OT_ID, EMPLOYEE_CODE, OT_DATE, OT_TYPE, STATUS FROM HRMS.OVERTIME_REQUESTS WHERE OT_ID = 'OT-2026-000003';
SELECT LOG_ID, ATTENDANCE_DATE, CHECK_IN_TIME, STATUS FROM HRMS.ATTENDANCE_LOGS WHERE LOG_ID = 2367;

-- 2. Remove the test rows together.
DECLARE
  v_leave_rows NUMBER;
BEGIN
  -- Leave request 26 (only the approved 1-day Casual Leave on 2026-10-05 for employee 1019).
  DELETE FROM HRMS.LEAVE_REQUEST_ATTACHMENT_CONTENT WHERE LEAVE_REQ_ID = 26;

  DELETE FROM HRMS.LEAVE_REQUESTS
   WHERE LEAVE_REQ_ID = 26
     AND EMPLOYEE_ID  = 1019
     AND LEAVE_TYPE   = 'CASUAL'
     AND START_DATE   = DATE '2026-10-05'
     AND END_DATE     = DATE '2026-10-05'
     AND STATUS       = 'APPROVED';
  v_leave_rows := SQL%ROWCOUNT;

  -- Give the day back only when the request really was deleted just now.
  IF v_leave_rows = 1 THEN
    UPDATE HRMS.LEAVE_BALANCES
       SET USED_DAYS = USED_DAYS - 1
     WHERE EMPLOYEE_ID = 1019 AND LEAVE_TYPE = 'CASUAL' AND YEAR = 2026 AND USED_DAYS >= 1;
    IF SQL%ROWCOUNT <> 1 THEN
      RAISE_APPLICATION_ERROR(-20001, 'CASUAL balance not restored (USED_DAYS < 1?). ROLLBACK and check by hand.');
    END IF;
  ELSE
    DBMS_OUTPUT.PUT_LINE('Leave 26: nothing deleted (already cleaned up, or not the expected row).');
  END IF;

  -- Overtime request.
  DELETE FROM HRMS.OVERTIME_REQUESTS
   WHERE OT_ID = 'OT-2026-000003' AND EMPLOYEE_ID = 1019 AND OT_DATE = DATE '2026-10-04';
  DBMS_OUTPUT.PUT_LINE('Overtime rows deleted: ' || SQL%ROWCOUNT);

  -- The check-in on the Sunday (a day record made by a real check-in, still open: no check-out).
  DELETE FROM HRMS.ATTENDANCE_REGULARIZATION WHERE LOG_ID = 2367;
  DELETE FROM HRMS.ATTENDANCE_LOGS
   WHERE LOG_ID = 2367 AND EMPLOYEE_ID = 1019 AND ATTENDANCE_DATE = DATE '2026-10-04' AND CHECK_OUT_TIME IS NULL;
  DBMS_OUTPUT.PUT_LINE('Attendance log rows deleted: ' || SQL%ROWCOUNT);
END;
/

-- 3. After (leave 26, OT and log 2367 should return no rows; CASUAL USED_DAYS should be 0).
SELECT LEAVE_REQ_ID FROM HRMS.LEAVE_REQUESTS WHERE LEAVE_REQ_ID = 26;
SELECT USED_DAYS FROM HRMS.LEAVE_BALANCES WHERE EMPLOYEE_ID = 1019 AND YEAR = 2026 AND LEAVE_TYPE = 'CASUAL';
SELECT OT_ID FROM HRMS.OVERTIME_REQUESTS WHERE OT_ID = 'OT-2026-000003';
SELECT LOG_ID FROM HRMS.ATTENDANCE_LOGS WHERE LOG_ID = 2367;

-- COMMIT;
-- ROLLBACK;
