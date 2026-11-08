-- Data clean-up of the masters, decided 2026-11-08 after the masters code review. Run as the HRMS user, by hand (or with
-- tools/RunSql.java, which runs it in one transaction). It does not need V26. Not applied yet. Run it ONCE: a second run
-- stops at the first duplicate holiday and changes nothing (the holiday table is unique on date + name).
--
-- 1. Holidays: South Sudan public holidays from https://www.qppstudio.net/public-holidays/south_sudan.htm (that page lists
--    October 2026 to September 2027). The Omani leftovers go: the "Test" row and the 18/19 Nov "National Day" rows are
--    deleted, Islamic New Year and Prophet Birthday 2026 are switched off (past days; they keep their history).
--    The 2026 New Year and Christmas rows stay and are recurring, so they repeat in later years by themselves.
--    Fixed-date holidays are entered once as recurring (from the year shown) and repeat every year; the moving ones
--    (Eid, Easter) are entered for 2027 only and must be entered again each year.
--    Eid Al Adha Holiday (17 May 2027) is listed as "only for Muslims", so it is OPTIONAL and not a day off for everyone.
-- 2. Work shift SPLIT: 08:00 - 18:00 with the 120 minute break is 8 working hours (it was 19:00, which gave 9).
-- 3. Duplicate designations: the six titles that existed twice (in different departments) lose the copy nobody uses
--    (no employee job record and no requisition points to it): BA, DBA, PROC_OFF, QA_ENG, SR_SWE, SWE.
--    The copies in use (BAPP_BA, BAPP_DBA, PROC_OFFICER, BAPP_QA, BAPP_SR_ENG, BAPP_ENG) stay.

DELETE FROM HRMS.HOLIDAY_CALENDAR WHERE HOLIDAY_NAME = 'Test' AND HOLIDAY_DATE = DATE '2026-01-01';

DELETE FROM HRMS.HOLIDAY_CALENDAR
 WHERE HOLIDAY_NAME IN ('National Day', 'National Day Holiday')
   AND HOLIDAY_DATE IN (DATE '2026-11-18', DATE '2026-11-19');

UPDATE HRMS.HOLIDAY_CALENDAR SET IS_ACTIVE = 0
 WHERE HOLIDAY_NAME IN ('Islamic New Year', 'Prophet Birthday') AND HOLIDAY_DATE IN (DATE '2026-06-17', DATE '2026-09-14');

INSERT INTO HRMS.HOLIDAY_CALENDAR (HOLIDAY_ID, HOLIDAY_NAME, HOLIDAY_NAME_AR, HOLIDAY_DATE, HOLIDAY_TYPE, DESCRIPTION, IS_RECURRING, YEAR, IS_ACTIVE, CREATED_BY)
SELECT HRMS.SEQ_HOLIDAY_CALENDAR.NEXTVAL, h.name, h.name_ar, h.day, h.kind, 'South Sudan public holiday', h.recurring, EXTRACT(YEAR FROM h.day), 1, 'DATA_CLEANUP'
  FROM (
        SELECT 'Christmas Holiday' AS name, N'عطلة عيد الميلاد' AS name_ar, DATE '2026-12-24' AS day, 'PUBLIC' AS kind, 1 AS recurring FROM dual
  UNION ALL SELECT 'Christmas Holiday', N'عطلة عيد الميلاد', DATE '2026-12-26', 'PUBLIC', 1 FROM dual
  UNION ALL SELECT 'International Labour Day', N'عيد العمال العالمي', DATE '2027-05-01', 'PUBLIC', 1 FROM dual
  UNION ALL SELECT 'SPLA Day', N'يوم الجيش الشعبي لتحرير السودان', DATE '2027-05-16', 'PUBLIC', 1 FROM dual
  UNION ALL SELECT 'SPLA Day Holiday', N'عطلة يوم الجيش الشعبي لتحرير السودان', DATE '2027-05-17', 'PUBLIC', 1 FROM dual
  UNION ALL SELECT 'Independence Day', N'يوم الاستقلال', DATE '2027-07-09', 'PUBLIC', 1 FROM dual
  UNION ALL SELECT 'Martyrs'' Day', N'يوم الشهداء', DATE '2027-07-30', 'PUBLIC', 1 FROM dual
  UNION ALL SELECT 'Eid Al Fitr', N'عيد الفطر', DATE '2027-03-09', 'RELIGIOUS', 0 FROM dual
  UNION ALL SELECT 'Eid Al Fitr Holiday', N'عطلة عيد الفطر', DATE '2027-03-10', 'RELIGIOUS', 0 FROM dual
  UNION ALL SELECT 'Eid Al Fitr Holiday', N'عطلة عيد الفطر', DATE '2027-03-11', 'RELIGIOUS', 0 FROM dual
  UNION ALL SELECT 'Good Friday', N'الجمعة العظيمة', DATE '2027-03-26', 'RELIGIOUS', 0 FROM dual
  UNION ALL SELECT 'Holy Saturday', N'سبت النور', DATE '2027-03-27', 'RELIGIOUS', 0 FROM dual
  UNION ALL SELECT 'Easter Sunday', N'أحد الفصح', DATE '2027-03-28', 'RELIGIOUS', 0 FROM dual
  UNION ALL SELECT 'Easter Monday', N'اثنين الفصح', DATE '2027-03-29', 'RELIGIOUS', 0 FROM dual
  UNION ALL SELECT 'Eid Al Adha', N'عيد الأضحى', DATE '2027-05-16', 'RELIGIOUS', 0 FROM dual
  UNION ALL SELECT 'Eid Al Adha Holiday', N'عطلة عيد الأضحى', DATE '2027-05-17', 'OPTIONAL', 0 FROM dual
  ) h;

UPDATE HRMS.WORK_SHIFTS SET END_TIME = '18:00' WHERE SHIFT_CODE = 'SPLIT' AND END_TIME = '19:00';

DELETE FROM HRMS.DESIGNATIONS d
 WHERE d.CODE IN ('BA', 'DBA', 'PROC_OFF', 'QA_ENG', 'SR_SWE', 'SWE')
   AND NOT EXISTS (SELECT 1 FROM HRMS.EMPLOYEE_JOB_DETAILS j WHERE j.DESIGNATION_ID = d.DESIG_ID)
   AND NOT EXISTS (SELECT 1 FROM HRMS.JOB_REQUISITIONS r WHERE r.DESIGNATION_ID = d.DESIG_ID);

COMMIT;
