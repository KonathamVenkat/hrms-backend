package com.hrms.employee;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V28 (pay scale) against a real Oracle schema built from db/migration: one starting scale per grade, increment and
 * effective date, only for a known grade, increment 1 or more, a positive amount and a whole-day date.
 */
@Transactional
class SalaryGradeScaleSchemaDbTest extends DatabaseTest {

    private static final long GRADE = 990801;

    @Autowired JdbcTemplate jdbc;

    private void grade() {
        jdbc.update("insert into HRMS.GRADES (GRADE_ID, GRADES_CODE, GRADES_NAME, GRADES_NAME_AR, IS_ACTIVE, VERSION) "
            + "values (?, 'GV28', 'V28 grade', N'اختبار', 1, 0)", GRADE);
    }

    private int scale(long gradeId, int increment, String effectiveFrom, String amount) {
        return jdbc.update("insert into HRMS.SALARY_GRADE_SCALE (GRADE_ID, INCREMENT_NO, EFFECTIVE_FROM, STARTING_SCALE) "
            + "values (?, ?, " + effectiveFrom + ", " + amount + ")", gradeId, increment);
    }

    @Test
    void aGradeHasOneStartingScalePerIncrementAndDate() {
        grade();
        assertEquals(1, scale(GRADE, 1, "date '2026-03-01'", "460462"));
        assertEquals(1, scale(GRADE, 2, "date '2026-03-01'", "483485"));
        // a later scale for the same increment is a new row; the March one stays
        assertEquals(1, scale(GRADE, 1, "date '2027-01-01'", "483485"));
        assertThrows(DataIntegrityViolationException.class, () -> scale(GRADE, 1, "date '2026-03-01'", "1"));
        assertEquals(3L, jdbc.queryForObject(
            "select count(*) from HRMS.SALARY_GRADE_SCALE where GRADE_ID = ?", Long.class, GRADE));
    }

    @Test
    void anUnknownGradeAZeroIncrementANonPositiveAmountAndATimeOfDayAreRefused() {
        grade();
        assertThrows(DataIntegrityViolationException.class, () -> scale(999999999L, 1, "date '2026-03-01'", "1"));
        assertThrows(DataIntegrityViolationException.class, () -> scale(GRADE, 0, "date '2026-03-01'", "1"));
        assertThrows(DataIntegrityViolationException.class, () -> scale(GRADE, 1, "date '2026-03-01'", "0"));
        assertThrows(DataIntegrityViolationException.class,
            () -> scale(GRADE, 1, "to_date('2026-03-01 10:30', 'YYYY-MM-DD HH24:MI')", "1"));
    }

    @Test
    void auditColumnsAndVersionDefault() {
        grade();
        scale(GRADE, 3, "date '2026-03-01'", "507659");
        assertEquals(0L, jdbc.queryForObject("select VERSION from HRMS.SALARY_GRADE_SCALE where GRADE_ID = ? "
            + "and INCREMENT_NO = 3", Long.class, GRADE));
        assertEquals(1L, jdbc.queryForObject("select count(*) from HRMS.SALARY_GRADE_SCALE where GRADE_ID = ? "
            + "and CREATED_AT is not null and UPDATED_AT is not null", Long.class, GRADE));
    }
}
