-- UNDO of db/seed_grades_uhrpm.sql: removes grades G01 to G24.
-- Run as the HRMS user. Oracle refuses (ORA-02292) a grade that a designation or an employee's job record already
-- uses, so unlink those first.

DELETE FROM HRMS.GRADES
 WHERE GRADES_CODE IN ('G01', 'G02', 'G03', 'G04', 'G05', 'G06', 'G07', 'G08', 'G09', 'G10', 'G11', 'G12',
                       'G13', 'G14', 'G15', 'G16', 'G17', 'G18', 'G19', 'G20', 'G21', 'G22', 'G23', 'G24');

COMMIT;
