-- Removes konatham venkat's test check-in on Sunday 2026-10-04.
--
-- EMP-2026-1018 (EMPLOYEE_ID 1018), ATTENDANCE_LOGS id 490: checked in 12:51, out 17:28 (277 minutes, LATE) on the
-- Sunday. It was made on 2026-10-04 before the weekend moved to Saturday/Sunday, and he has no overtime request for
-- that day, so under the current rules this check-in would be refused. His approved Sick Leave (id 25) is left alone.
--
-- Deletes only that exact row (and any regularization rows pointing at it; there are none today). Running the block
-- twice changes nothing the second time. Run the whole block, check the SELECTs, then COMMIT (or ROLLBACK).
-- AFTER the COMMIT, re-run the attendance back-fill for that day (as HR_ADMIN) so Sunday becomes a WEEKEND row again
-- and his October summary is recalculated:
--   POST /api/v1/attendance/admin/day-records?from=2026-10-04&to=2026-10-04

SET SERVEROUTPUT ON

-- 1. Before.
SELECT LOG_ID, EMPLOYEE_CODE, ATTENDANCE_DATE, CHECK_IN_TIME, CHECK_OUT_TIME, STATUS
  FROM HRMS.ATTENDANCE_LOGS WHERE LOG_ID = 490;

-- 2. Delete.
BEGIN
  DELETE FROM HRMS.ATTENDANCE_REGULARIZATION WHERE LOG_ID = 490;
  DELETE FROM HRMS.ATTENDANCE_LOGS
   WHERE LOG_ID = 490 AND EMPLOYEE_ID = 1018 AND ATTENDANCE_DATE = DATE '2026-10-04' AND CREATED_BY = 'venkat';
  DBMS_OUTPUT.PUT_LINE('Attendance log rows deleted: ' || SQL%ROWCOUNT);
END;
/

-- 3. After (should return no rows).
SELECT LOG_ID FROM HRMS.ATTENDANCE_LOGS WHERE LOG_ID = 490;

-- COMMIT;
-- ROLLBACK;
