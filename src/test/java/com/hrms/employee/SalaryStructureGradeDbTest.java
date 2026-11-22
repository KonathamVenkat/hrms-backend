package com.hrms.employee;

import com.hrms.employee.repository.GradeRepository;
import com.hrms.payroll.repository.SalaryStructureRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V30 against a real Oracle schema: a structure may name a known grade, one structure per grade, any number without one;
 * a grade's structure is found by grade; the starting scale on a date is the latest row from on or before it.
 */
@Transactional
class SalaryStructureGradeDbTest extends DatabaseTest {

    private static final long GRADE = 993001;
    private static final long OTHER_GRADE = 993002;

    @Autowired JdbcTemplate jdbc;
    @Autowired SalaryStructureRepository structures;
    @Autowired GradeRepository gradeRepository;

    private void grades() {
        for (long id : new long[] {GRADE, OTHER_GRADE}) {
            jdbc.update("insert into HRMS.GRADES (GRADE_ID, GRADES_CODE, GRADES_NAME, GRADES_NAME_AR, IS_ACTIVE, VERSION) "
                + "values (?, ?, ?, N'درجة', 1, 0)", id, "V30G" + id, "V30 grade " + id);
        }
    }

    private int structure(long id, Long gradeId) {
        return jdbc.update("insert into HRMS.SALARY_STRUCTURES (STRUCTURE_ID, STRUCTURE_CODE, STRUCTURE_NAME, IS_ACTIVE, "
            + "GRADE_ID, VERSION) values (?, ?, ?, 1, ?, 0)", id, "V30S" + id, "V30 structure " + id, gradeId);
    }

    @Test
    void oneStructurePerGrade_anyNumberWithoutOne_andOnlyAKnownGrade() {
        grades();
        assertEquals(1, structure(993101, GRADE));
        assertThrows(DataIntegrityViolationException.class, () -> structure(993102, GRADE));
        assertEquals(1, structure(993103, OTHER_GRADE));
        assertEquals(1, structure(993104, null));
        assertEquals(1, structure(993105, null));
        assertThrows(DataIntegrityViolationException.class, () -> structure(993106, 999999999L));
    }

    @Test
    void aGradesStructureIsFoundByGrade() {
        grades();
        structure(993101, GRADE);
        assertEquals(993101L, structures.findByGradeId(GRADE).orElseThrow().getStructureId());
        assertTrue(structures.findByGradeId(OTHER_GRADE).isEmpty());
    }

    @Test
    void theStartingScaleOnADateIsTheLatestRowFromOnOrBeforeIt() {
        grades();
        jdbc.update("insert into HRMS.SALARY_GRADE_SCALE (GRADE_ID, INCREMENT_NO, EFFECTIVE_FROM, STARTING_SCALE) "
            + "values (?, 1, date '2026-03-01', 460462)", GRADE);
        jdbc.update("insert into HRMS.SALARY_GRADE_SCALE (GRADE_ID, INCREMENT_NO, EFFECTIVE_FROM, STARTING_SCALE) "
            + "values (?, 1, date '2027-01-01', 483485)", GRADE);

        assertTrue(gradeRepository.findStartingScale(GRADE, 1, LocalDate.of(2026, 2, 28)).isEmpty());
        assertEquals(0, new BigDecimal("460462").compareTo(
            gradeRepository.findStartingScale(GRADE, 1, LocalDate.of(2026, 3, 1)).orElseThrow()));
        assertEquals(0, new BigDecimal("460462").compareTo(
            gradeRepository.findStartingScale(GRADE, 1, LocalDate.of(2026, 12, 31)).orElseThrow()));
        assertEquals(0, new BigDecimal("483485").compareTo(
            gradeRepository.findStartingScale(GRADE, 1, LocalDate.of(2027, 6, 1)).orElseThrow()));
        assertTrue(gradeRepository.findStartingScale(GRADE, 2, LocalDate.of(2027, 6, 1)).isEmpty());
    }
}
