-- Moves the work shifts from the Oman week (works Sun-Thu, weekend Fri/Sat) to the South Sudan
-- week (works Mon-Fri, weekend Sat/Sun).
--
-- The attendance classifier takes a shift's WORKING_DAYS as the truth: any day not listed is the
-- employee's weekly off. The company default (Sat/Sun) only applies to employees with no shift,
-- so the shifts must be changed too. Only rows that still hold the old Sun-Thu pattern are
-- touched; a shift with any other pattern (e.g. Evening, which works all seven days) is left alone.
--
-- After running, re-run the attendance back-fill for the days already generated:
--   POST /api/v1/attendance/admin/day-records?from=YYYY-MM-DD&to=YYYY-MM-DD   (max 62 days per call)

-- 1. Look first: which shifts exist and what they hold now.
SELECT SHIFT_CODE, SHIFT_NAME, WORKING_DAYS, IS_ACTIVE FROM HRMS.WORK_SHIFTS ORDER BY SHIFT_CODE;

-- 2. Change the Sun-Thu shifts to Mon-Fri.
UPDATE HRMS.WORK_SHIFTS
   SET WORKING_DAYS = 'MON,TUE,WED,THU,FRI'
 WHERE REPLACE(UPPER(WORKING_DAYS), ' ', '') = 'SUN,MON,TUE,WED,THU';

-- 3. Check the result, then COMMIT (or ROLLBACK).
SELECT SHIFT_CODE, SHIFT_NAME, WORKING_DAYS FROM HRMS.WORK_SHIFTS ORDER BY SHIFT_CODE;
-- COMMIT;

-- Undo (only if you have not changed these shifts by hand since):
-- UPDATE HRMS.WORK_SHIFTS SET WORKING_DAYS = 'SUN,MON,TUE,WED,THU'
--  WHERE WORKING_DAYS = 'MON,TUE,WED,THU,FRI';
