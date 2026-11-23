-- Repairs salary history written before 2026-11-23, when replacing a salary ended the old record on the day the change was
-- entered instead of the day before the new record starts. That left a gap (future-dated change) or an overlap (same-day or
-- back-dated change). Sets each closed record's EFFECTIVE_TO to the next record's EFFECTIVE_FROM - 1 (same employee, by start
-- date) wherever it differs. Current records and the latest record are untouched. Data only, safe to run twice (0 rows then).
-- Run as the HRMS user, before any payroll run reads these dates.
MERGE INTO HRMS.EMPLOYEE_SALARY t
USING (
    SELECT EMP_SALARY_ID, NEXT_FROM - 1 AS LAST_DAY
      FROM (SELECT EMP_SALARY_ID, EFFECTIVE_TO, IS_CURRENT,
                   LEAD(EFFECTIVE_FROM) OVER (PARTITION BY EMPLOYEE_ID ORDER BY EFFECTIVE_FROM, EMP_SALARY_ID) AS NEXT_FROM
              FROM HRMS.EMPLOYEE_SALARY)
     WHERE IS_CURRENT = 0
       AND NEXT_FROM IS NOT NULL
       AND (EFFECTIVE_TO IS NULL OR EFFECTIVE_TO <> NEXT_FROM - 1)
) src
ON (t.EMP_SALARY_ID = src.EMP_SALARY_ID)
WHEN MATCHED THEN UPDATE SET t.EFFECTIVE_TO = src.LAST_DAY;
-- Expect on dev (2026-11-23): 2 rows (Konatam Venkat: 2026-03-01 record -> ends 2026-11-21; 2026-11-22 record -> ends 2026-11-30).

COMMIT;
