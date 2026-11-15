-- Salary structures by grade, from the March 2026 salary sheet (Salary_Structure_March_2026_English.xlsx):
-- 13 structures (GRADE_01 to GRADE_12 and THIRD_RANGE) with 110 items.
--
-- A structure holds only what the grade decides, the same for everyone on it:
--   % of basic : Cost-of-Living 20, Housing 20, Nature-of-Work 56, Transfer 35, and Administration 49 for grades 1-5
--                and Third Range only. (The sheet takes Nature-of-Work 40%, Transfer 25% and Administration 35% of
--                Total Basic; Total Basic = 1.4 x basic.)
--   fixed      : Petrol 337,500 for grades 1-5 and Third Range, 236,250 for grades 6-8, 202,500 for grades 9-12.
--   % of gross : Insurance 8, Armed Forces 3.33 (the sheet uses 1/30 = 3.3333; the column keeps 2 decimals).
--   formula    : Income Tax (shows 0 until a tax table exists).
-- NOT in any structure (they differ per person; to come with per-employee items and the loans module):
--   Social allowance, Qualification allowance, the advances / loans, Penalty, Zakat, Minister's deduction.
-- The basic itself is the starting scale for the employee's grade and step, entered when the salary is assigned.
-- Not covered: the three top posts with no grade (sheet rows 1-3).
--
-- Run as the HRMS user, AFTER db/salary_components_mar2026.sql and db/deactivate_nssf_components.sql.
-- DBeaver: select from DECLARE down to the "/" line and press Ctrl+Enter, or run the whole file with Alt+X.
-- Everything runs in one block: if any component is missing or inactive the block stops and nothing is saved.
-- Safe to run twice: an existing structure (same code or name) and an existing item are skipped. Ends with COMMIT.
-- Undo: db/salary_structures_by_grade_rollback.sql
-- Check afterwards:
--   SELECT s.STRUCTURE_CODE, COUNT(i.ITEM_ID) FROM HRMS.SALARY_STRUCTURES s
--     LEFT JOIN HRMS.SALARY_STRUCTURE_ITEMS i ON i.STRUCTURE_ID = s.STRUCTURE_ID GROUP BY s.STRUCTURE_CODE ORDER BY 1;
--   Expect 9 items for GRADE_01 to GRADE_05 and THIRD_RANGE, 8 for GRADE_06 to GRADE_12 (110 in all).

DECLARE
  v_structures NUMBER;
  v_items      NUMBER;
BEGIN
  -- 1. Every component the structures use must exist and be active, otherwise stop before anything is written.
  FOR bad IN (SELECT need.code, c.IS_ACTIVE
                FROM (SELECT 'ADMIN_ALLOW' AS code FROM DUAL
      UNION ALL SELECT 'ARMED_FORCES' AS code FROM DUAL
      UNION ALL SELECT 'COLA' AS code FROM DUAL
      UNION ALL SELECT 'HRA' AS code FROM DUAL
      UNION ALL SELECT 'INCOME_TAX' AS code FROM DUAL
      UNION ALL SELECT 'INSURANCE' AS code FROM DUAL
      UNION ALL SELECT 'NATURE_OF_WORK' AS code FROM DUAL
      UNION ALL SELECT 'PETROL' AS code FROM DUAL
      UNION ALL SELECT 'TRANSFER_ALLOW' AS code FROM DUAL) need
                LEFT JOIN HRMS.SALARY_COMPONENTS c ON c.COMPONENT_CODE = need.code
               WHERE c.COMPONENT_ID IS NULL OR c.IS_ACTIVE <> 1) LOOP
    RAISE_APPLICATION_ERROR(-20001, 'Component ' || bad.code || CASE WHEN bad.IS_ACTIVE IS NULL
      THEN ' does not exist: run db/salary_components_mar2026.sql first.' ELSE ' is inactive.' END);
  END LOOP;
  -- 2. The structures (an existing code or name is skipped).
  FOR s IN (
      SELECT 'GRADE_01' AS code, 'Grade 1' AS name, 'Grade 1, from the March 2026 salary sheet. Basic = starting scale for the grade and step (entered at assignment). Of basic: Cost-of-Living 20%, Housing 20%, Nature-of-Work 56%, Administration 49%, Transfer 35%. Petrol 337,500. Of gross: Insurance 8%, Armed Forces 3.33%. Social, Qualification, advances and tax are not in the structure yet.' AS descr FROM DUAL
      UNION ALL SELECT 'GRADE_02' AS code, 'Grade 2' AS name, 'Grade 2, from the March 2026 salary sheet. Basic = starting scale for the grade and step (entered at assignment). Of basic: Cost-of-Living 20%, Housing 20%, Nature-of-Work 56%, Administration 49%, Transfer 35%. Petrol 337,500. Of gross: Insurance 8%, Armed Forces 3.33%. Social, Qualification, advances and tax are not in the structure yet.' AS descr FROM DUAL
      UNION ALL SELECT 'GRADE_03' AS code, 'Grade 3' AS name, 'Grade 3, from the March 2026 salary sheet. Basic = starting scale for the grade and step (entered at assignment). Of basic: Cost-of-Living 20%, Housing 20%, Nature-of-Work 56%, Administration 49%, Transfer 35%. Petrol 337,500. Of gross: Insurance 8%, Armed Forces 3.33%. Social, Qualification, advances and tax are not in the structure yet.' AS descr FROM DUAL
      UNION ALL SELECT 'GRADE_04' AS code, 'Grade 4' AS name, 'Grade 4, from the March 2026 salary sheet. Basic = starting scale for the grade and step (entered at assignment). Of basic: Cost-of-Living 20%, Housing 20%, Nature-of-Work 56%, Administration 49%, Transfer 35%. Petrol 337,500. Of gross: Insurance 8%, Armed Forces 3.33%. Social, Qualification, advances and tax are not in the structure yet.' AS descr FROM DUAL
      UNION ALL SELECT 'GRADE_05' AS code, 'Grade 5' AS name, 'Grade 5, from the March 2026 salary sheet. Basic = starting scale for the grade and step (entered at assignment). Of basic: Cost-of-Living 20%, Housing 20%, Nature-of-Work 56%, Administration 49%, Transfer 35%. Petrol 337,500. Of gross: Insurance 8%, Armed Forces 3.33%. Social, Qualification, advances and tax are not in the structure yet.' AS descr FROM DUAL
      UNION ALL SELECT 'GRADE_06' AS code, 'Grade 6' AS name, 'Grade 6, from the March 2026 salary sheet. Basic = starting scale for the grade and step (entered at assignment). Of basic: Cost-of-Living 20%, Housing 20%, Nature-of-Work 56%, Transfer 35%. Petrol 236,250. Of gross: Insurance 8%, Armed Forces 3.33%. Social, Qualification, advances and tax are not in the structure yet.' AS descr FROM DUAL
      UNION ALL SELECT 'GRADE_07' AS code, 'Grade 7' AS name, 'Grade 7, from the March 2026 salary sheet. Basic = starting scale for the grade and step (entered at assignment). Of basic: Cost-of-Living 20%, Housing 20%, Nature-of-Work 56%, Transfer 35%. Petrol 236,250. Of gross: Insurance 8%, Armed Forces 3.33%. Social, Qualification, advances and tax are not in the structure yet.' AS descr FROM DUAL
      UNION ALL SELECT 'GRADE_08' AS code, 'Grade 8' AS name, 'Grade 8, from the March 2026 salary sheet. Basic = starting scale for the grade and step (entered at assignment). Of basic: Cost-of-Living 20%, Housing 20%, Nature-of-Work 56%, Transfer 35%. Petrol 236,250. Of gross: Insurance 8%, Armed Forces 3.33%. Social, Qualification, advances and tax are not in the structure yet.' AS descr FROM DUAL
      UNION ALL SELECT 'GRADE_09' AS code, 'Grade 9' AS name, 'Grade 9, from the March 2026 salary sheet. Basic = starting scale for the grade and step (entered at assignment). Of basic: Cost-of-Living 20%, Housing 20%, Nature-of-Work 56%, Transfer 35%. Petrol 202,500. Of gross: Insurance 8%, Armed Forces 3.33%. Social, Qualification, advances and tax are not in the structure yet.' AS descr FROM DUAL
      UNION ALL SELECT 'GRADE_10' AS code, 'Grade 10' AS name, 'Grade 10, from the March 2026 salary sheet. Basic = starting scale for the grade and step (entered at assignment). Of basic: Cost-of-Living 20%, Housing 20%, Nature-of-Work 56%, Transfer 35%. Petrol 202,500. Of gross: Insurance 8%, Armed Forces 3.33%. Social, Qualification, advances and tax are not in the structure yet.' AS descr FROM DUAL
      UNION ALL SELECT 'GRADE_11' AS code, 'Grade 11' AS name, 'Grade 11, from the March 2026 salary sheet. Basic = starting scale for the grade and step (entered at assignment). Of basic: Cost-of-Living 20%, Housing 20%, Nature-of-Work 56%, Transfer 35%. Petrol 202,500. Of gross: Insurance 8%, Armed Forces 3.33%. Social, Qualification, advances and tax are not in the structure yet.' AS descr FROM DUAL
      UNION ALL SELECT 'GRADE_12' AS code, 'Grade 12' AS name, 'Grade 12, from the March 2026 salary sheet. Basic = starting scale for the grade and step (entered at assignment). Of basic: Cost-of-Living 20%, Housing 20%, Nature-of-Work 56%, Transfer 35%. Petrol 202,500. Of gross: Insurance 8%, Armed Forces 3.33%. Social, Qualification, advances and tax are not in the structure yet.' AS descr FROM DUAL
      UNION ALL SELECT 'THIRD_RANGE' AS code, 'Third Range' AS name, 'Third Range (one employee in March 2026; handled like grades 1-5), from the March 2026 salary sheet. Basic = starting scale for the grade and step (entered at assignment). Of basic: Cost-of-Living 20%, Housing 20%, Nature-of-Work 56%, Administration 49%, Transfer 35%. Petrol 337,500. Of gross: Insurance 8%, Armed Forces 3.33%. Social, Qualification, advances and tax are not in the structure yet.' AS descr FROM DUAL
  ) LOOP
    INSERT INTO HRMS.SALARY_STRUCTURES (STRUCTURE_ID, STRUCTURE_CODE, STRUCTURE_NAME, DESCRIPTION, IS_ACTIVE,
                                        CREATED_BY, CREATED_AT, UPDATED_AT, VERSION)
    SELECT HRMS.SEQ_SALARY_STRUCTURES.NEXTVAL, s.code, s.name, s.descr, 1, 'SYSTEM', SYSTIMESTAMP, SYSTIMESTAMP, 0
      FROM DUAL
     WHERE NOT EXISTS (SELECT 1 FROM HRMS.SALARY_STRUCTURES x
                        WHERE x.STRUCTURE_CODE = s.code OR UPPER(x.STRUCTURE_NAME) = UPPER(s.name));
  END LOOP;
  -- 3. The items (an existing structure + component pair is skipped).
  FOR i IN (
      SELECT 'GRADE_01' AS scode, 'COLA' AS ccode, 0 AS amount, 20 AS pct, 1 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_01' AS scode, 'HRA' AS ccode, 0 AS amount, 20 AS pct, 2 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_01' AS scode, 'NATURE_OF_WORK' AS ccode, 0 AS amount, 56 AS pct, 3 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_01' AS scode, 'ADMIN_ALLOW' AS ccode, 0 AS amount, 49 AS pct, 4 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_01' AS scode, 'TRANSFER_ALLOW' AS ccode, 0 AS amount, 35 AS pct, 5 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_01' AS scode, 'PETROL' AS ccode, 337500 AS amount, 0 AS pct, 6 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_01' AS scode, 'INSURANCE' AS ccode, 0 AS amount, 8 AS pct, 7 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_01' AS scode, 'ARMED_FORCES' AS ccode, 0 AS amount, 3.33 AS pct, 8 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_01' AS scode, 'INCOME_TAX' AS ccode, 0 AS amount, 0 AS pct, 9 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_02' AS scode, 'COLA' AS ccode, 0 AS amount, 20 AS pct, 1 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_02' AS scode, 'HRA' AS ccode, 0 AS amount, 20 AS pct, 2 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_02' AS scode, 'NATURE_OF_WORK' AS ccode, 0 AS amount, 56 AS pct, 3 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_02' AS scode, 'ADMIN_ALLOW' AS ccode, 0 AS amount, 49 AS pct, 4 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_02' AS scode, 'TRANSFER_ALLOW' AS ccode, 0 AS amount, 35 AS pct, 5 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_02' AS scode, 'PETROL' AS ccode, 337500 AS amount, 0 AS pct, 6 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_02' AS scode, 'INSURANCE' AS ccode, 0 AS amount, 8 AS pct, 7 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_02' AS scode, 'ARMED_FORCES' AS ccode, 0 AS amount, 3.33 AS pct, 8 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_02' AS scode, 'INCOME_TAX' AS ccode, 0 AS amount, 0 AS pct, 9 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_03' AS scode, 'COLA' AS ccode, 0 AS amount, 20 AS pct, 1 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_03' AS scode, 'HRA' AS ccode, 0 AS amount, 20 AS pct, 2 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_03' AS scode, 'NATURE_OF_WORK' AS ccode, 0 AS amount, 56 AS pct, 3 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_03' AS scode, 'ADMIN_ALLOW' AS ccode, 0 AS amount, 49 AS pct, 4 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_03' AS scode, 'TRANSFER_ALLOW' AS ccode, 0 AS amount, 35 AS pct, 5 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_03' AS scode, 'PETROL' AS ccode, 337500 AS amount, 0 AS pct, 6 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_03' AS scode, 'INSURANCE' AS ccode, 0 AS amount, 8 AS pct, 7 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_03' AS scode, 'ARMED_FORCES' AS ccode, 0 AS amount, 3.33 AS pct, 8 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_03' AS scode, 'INCOME_TAX' AS ccode, 0 AS amount, 0 AS pct, 9 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_04' AS scode, 'COLA' AS ccode, 0 AS amount, 20 AS pct, 1 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_04' AS scode, 'HRA' AS ccode, 0 AS amount, 20 AS pct, 2 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_04' AS scode, 'NATURE_OF_WORK' AS ccode, 0 AS amount, 56 AS pct, 3 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_04' AS scode, 'ADMIN_ALLOW' AS ccode, 0 AS amount, 49 AS pct, 4 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_04' AS scode, 'TRANSFER_ALLOW' AS ccode, 0 AS amount, 35 AS pct, 5 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_04' AS scode, 'PETROL' AS ccode, 337500 AS amount, 0 AS pct, 6 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_04' AS scode, 'INSURANCE' AS ccode, 0 AS amount, 8 AS pct, 7 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_04' AS scode, 'ARMED_FORCES' AS ccode, 0 AS amount, 3.33 AS pct, 8 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_04' AS scode, 'INCOME_TAX' AS ccode, 0 AS amount, 0 AS pct, 9 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_05' AS scode, 'COLA' AS ccode, 0 AS amount, 20 AS pct, 1 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_05' AS scode, 'HRA' AS ccode, 0 AS amount, 20 AS pct, 2 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_05' AS scode, 'NATURE_OF_WORK' AS ccode, 0 AS amount, 56 AS pct, 3 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_05' AS scode, 'ADMIN_ALLOW' AS ccode, 0 AS amount, 49 AS pct, 4 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_05' AS scode, 'TRANSFER_ALLOW' AS ccode, 0 AS amount, 35 AS pct, 5 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_05' AS scode, 'PETROL' AS ccode, 337500 AS amount, 0 AS pct, 6 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_05' AS scode, 'INSURANCE' AS ccode, 0 AS amount, 8 AS pct, 7 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_05' AS scode, 'ARMED_FORCES' AS ccode, 0 AS amount, 3.33 AS pct, 8 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_05' AS scode, 'INCOME_TAX' AS ccode, 0 AS amount, 0 AS pct, 9 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_06' AS scode, 'COLA' AS ccode, 0 AS amount, 20 AS pct, 1 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_06' AS scode, 'HRA' AS ccode, 0 AS amount, 20 AS pct, 2 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_06' AS scode, 'NATURE_OF_WORK' AS ccode, 0 AS amount, 56 AS pct, 3 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_06' AS scode, 'TRANSFER_ALLOW' AS ccode, 0 AS amount, 35 AS pct, 4 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_06' AS scode, 'PETROL' AS ccode, 236250 AS amount, 0 AS pct, 5 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_06' AS scode, 'INSURANCE' AS ccode, 0 AS amount, 8 AS pct, 6 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_06' AS scode, 'ARMED_FORCES' AS ccode, 0 AS amount, 3.33 AS pct, 7 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_06' AS scode, 'INCOME_TAX' AS ccode, 0 AS amount, 0 AS pct, 8 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_07' AS scode, 'COLA' AS ccode, 0 AS amount, 20 AS pct, 1 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_07' AS scode, 'HRA' AS ccode, 0 AS amount, 20 AS pct, 2 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_07' AS scode, 'NATURE_OF_WORK' AS ccode, 0 AS amount, 56 AS pct, 3 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_07' AS scode, 'TRANSFER_ALLOW' AS ccode, 0 AS amount, 35 AS pct, 4 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_07' AS scode, 'PETROL' AS ccode, 236250 AS amount, 0 AS pct, 5 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_07' AS scode, 'INSURANCE' AS ccode, 0 AS amount, 8 AS pct, 6 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_07' AS scode, 'ARMED_FORCES' AS ccode, 0 AS amount, 3.33 AS pct, 7 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_07' AS scode, 'INCOME_TAX' AS ccode, 0 AS amount, 0 AS pct, 8 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_08' AS scode, 'COLA' AS ccode, 0 AS amount, 20 AS pct, 1 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_08' AS scode, 'HRA' AS ccode, 0 AS amount, 20 AS pct, 2 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_08' AS scode, 'NATURE_OF_WORK' AS ccode, 0 AS amount, 56 AS pct, 3 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_08' AS scode, 'TRANSFER_ALLOW' AS ccode, 0 AS amount, 35 AS pct, 4 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_08' AS scode, 'PETROL' AS ccode, 236250 AS amount, 0 AS pct, 5 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_08' AS scode, 'INSURANCE' AS ccode, 0 AS amount, 8 AS pct, 6 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_08' AS scode, 'ARMED_FORCES' AS ccode, 0 AS amount, 3.33 AS pct, 7 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_08' AS scode, 'INCOME_TAX' AS ccode, 0 AS amount, 0 AS pct, 8 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_09' AS scode, 'COLA' AS ccode, 0 AS amount, 20 AS pct, 1 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_09' AS scode, 'HRA' AS ccode, 0 AS amount, 20 AS pct, 2 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_09' AS scode, 'NATURE_OF_WORK' AS ccode, 0 AS amount, 56 AS pct, 3 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_09' AS scode, 'TRANSFER_ALLOW' AS ccode, 0 AS amount, 35 AS pct, 4 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_09' AS scode, 'PETROL' AS ccode, 202500 AS amount, 0 AS pct, 5 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_09' AS scode, 'INSURANCE' AS ccode, 0 AS amount, 8 AS pct, 6 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_09' AS scode, 'ARMED_FORCES' AS ccode, 0 AS amount, 3.33 AS pct, 7 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_09' AS scode, 'INCOME_TAX' AS ccode, 0 AS amount, 0 AS pct, 8 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_10' AS scode, 'COLA' AS ccode, 0 AS amount, 20 AS pct, 1 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_10' AS scode, 'HRA' AS ccode, 0 AS amount, 20 AS pct, 2 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_10' AS scode, 'NATURE_OF_WORK' AS ccode, 0 AS amount, 56 AS pct, 3 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_10' AS scode, 'TRANSFER_ALLOW' AS ccode, 0 AS amount, 35 AS pct, 4 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_10' AS scode, 'PETROL' AS ccode, 202500 AS amount, 0 AS pct, 5 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_10' AS scode, 'INSURANCE' AS ccode, 0 AS amount, 8 AS pct, 6 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_10' AS scode, 'ARMED_FORCES' AS ccode, 0 AS amount, 3.33 AS pct, 7 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_10' AS scode, 'INCOME_TAX' AS ccode, 0 AS amount, 0 AS pct, 8 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_11' AS scode, 'COLA' AS ccode, 0 AS amount, 20 AS pct, 1 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_11' AS scode, 'HRA' AS ccode, 0 AS amount, 20 AS pct, 2 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_11' AS scode, 'NATURE_OF_WORK' AS ccode, 0 AS amount, 56 AS pct, 3 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_11' AS scode, 'TRANSFER_ALLOW' AS ccode, 0 AS amount, 35 AS pct, 4 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_11' AS scode, 'PETROL' AS ccode, 202500 AS amount, 0 AS pct, 5 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_11' AS scode, 'INSURANCE' AS ccode, 0 AS amount, 8 AS pct, 6 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_11' AS scode, 'ARMED_FORCES' AS ccode, 0 AS amount, 3.33 AS pct, 7 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_11' AS scode, 'INCOME_TAX' AS ccode, 0 AS amount, 0 AS pct, 8 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_12' AS scode, 'COLA' AS ccode, 0 AS amount, 20 AS pct, 1 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_12' AS scode, 'HRA' AS ccode, 0 AS amount, 20 AS pct, 2 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_12' AS scode, 'NATURE_OF_WORK' AS ccode, 0 AS amount, 56 AS pct, 3 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_12' AS scode, 'TRANSFER_ALLOW' AS ccode, 0 AS amount, 35 AS pct, 4 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_12' AS scode, 'PETROL' AS ccode, 202500 AS amount, 0 AS pct, 5 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_12' AS scode, 'INSURANCE' AS ccode, 0 AS amount, 8 AS pct, 6 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_12' AS scode, 'ARMED_FORCES' AS ccode, 0 AS amount, 3.33 AS pct, 7 AS sort_order FROM DUAL
      UNION ALL SELECT 'GRADE_12' AS scode, 'INCOME_TAX' AS ccode, 0 AS amount, 0 AS pct, 8 AS sort_order FROM DUAL
      UNION ALL SELECT 'THIRD_RANGE' AS scode, 'COLA' AS ccode, 0 AS amount, 20 AS pct, 1 AS sort_order FROM DUAL
      UNION ALL SELECT 'THIRD_RANGE' AS scode, 'HRA' AS ccode, 0 AS amount, 20 AS pct, 2 AS sort_order FROM DUAL
      UNION ALL SELECT 'THIRD_RANGE' AS scode, 'NATURE_OF_WORK' AS ccode, 0 AS amount, 56 AS pct, 3 AS sort_order FROM DUAL
      UNION ALL SELECT 'THIRD_RANGE' AS scode, 'ADMIN_ALLOW' AS ccode, 0 AS amount, 49 AS pct, 4 AS sort_order FROM DUAL
      UNION ALL SELECT 'THIRD_RANGE' AS scode, 'TRANSFER_ALLOW' AS ccode, 0 AS amount, 35 AS pct, 5 AS sort_order FROM DUAL
      UNION ALL SELECT 'THIRD_RANGE' AS scode, 'PETROL' AS ccode, 337500 AS amount, 0 AS pct, 6 AS sort_order FROM DUAL
      UNION ALL SELECT 'THIRD_RANGE' AS scode, 'INSURANCE' AS ccode, 0 AS amount, 8 AS pct, 7 AS sort_order FROM DUAL
      UNION ALL SELECT 'THIRD_RANGE' AS scode, 'ARMED_FORCES' AS ccode, 0 AS amount, 3.33 AS pct, 8 AS sort_order FROM DUAL
      UNION ALL SELECT 'THIRD_RANGE' AS scode, 'INCOME_TAX' AS ccode, 0 AS amount, 0 AS pct, 9 AS sort_order FROM DUAL
  ) LOOP
    INSERT INTO HRMS.SALARY_STRUCTURE_ITEMS (ITEM_ID, STRUCTURE_ID, COMPONENT_ID, CALC_TYPE_OVERRIDE, AMOUNT, PERCENTAGE,
                                             SORT_ORDER, IS_ACTIVE, CREATED_BY, CREATED_AT, UPDATED_AT)
    SELECT HRMS.SEQ_SALARY_STR_ITEMS.NEXTVAL, st.STRUCTURE_ID, c.COMPONENT_ID, NULL, i.amount, i.pct, i.sort_order, 1,
           'SYSTEM', SYSTIMESTAMP, SYSTIMESTAMP
      FROM HRMS.SALARY_STRUCTURES st, HRMS.SALARY_COMPONENTS c
     WHERE st.STRUCTURE_CODE = i.scode
       AND c.COMPONENT_CODE = i.ccode
       AND NOT EXISTS (SELECT 1 FROM HRMS.SALARY_STRUCTURE_ITEMS x
                        WHERE x.STRUCTURE_ID = st.STRUCTURE_ID AND x.COMPONENT_ID = c.COMPONENT_ID);
  END LOOP;
  -- 4. Final count; a wrong count undoes the whole block.
  SELECT COUNT(*) INTO v_structures FROM HRMS.SALARY_STRUCTURES WHERE STRUCTURE_CODE IN ('GRADE_01', 'GRADE_02', 'GRADE_03', 'GRADE_04', 'GRADE_05', 'GRADE_06', 'GRADE_07', 'GRADE_08', 'GRADE_09', 'GRADE_10', 'GRADE_11', 'GRADE_12', 'THIRD_RANGE');
  SELECT COUNT(*) INTO v_items
    FROM HRMS.SALARY_STRUCTURE_ITEMS it JOIN HRMS.SALARY_STRUCTURES st ON st.STRUCTURE_ID = it.STRUCTURE_ID
   WHERE st.STRUCTURE_CODE IN ('GRADE_01', 'GRADE_02', 'GRADE_03', 'GRADE_04', 'GRADE_05', 'GRADE_06', 'GRADE_07', 'GRADE_08', 'GRADE_09', 'GRADE_10', 'GRADE_11', 'GRADE_12', 'THIRD_RANGE');
  IF v_structures <> 13 OR v_items <> 110 THEN
    RAISE_APPLICATION_ERROR(-20003, 'Expected 13 structures and 110 items, found '
      || v_structures || ' and ' || v_items || '. Nothing was saved.');
  END IF;
  DBMS_OUTPUT.PUT_LINE(v_structures || ' structures, ' || v_items || ' items');
END;
/

COMMIT;
