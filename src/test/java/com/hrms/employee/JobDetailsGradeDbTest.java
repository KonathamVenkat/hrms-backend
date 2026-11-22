package com.hrms.employee;

import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.repository.GradeRepository;
import com.hrms.employee.repository.projection.GradeScaleStepProjection;
import com.hrms.employee.repository.projection.JobDetailsProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V29 against a real Oracle schema: grade and increment on a job record go together and need a known grade; the job
 * queries return them; the pay-scale queries pick the scale in effect on a date and skip inactive grades.
 */
@Transactional
class JobDetailsGradeDbTest extends DatabaseTest {

    private static final long GRADE = 990901;
    private static final long INACTIVE_GRADE = 990902;

    @Autowired JdbcTemplate jdbc;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired EmployeeJobDetailsRepository jobDetailsRepository;
    @Autowired GradeRepository gradeRepository;

    private Long employee() {
        return employeeRepository.saveAndFlush(Employee.builder()
            .firstName("Nyandeng").firstNameAr("ن").lastName("Garang").lastNameAr("ق")
            .dateOfBirth(LocalDate.of(1990, 5, 4)).gender(Gender.FEMALE)
            .personalEmail("v29-p@example.test").workEmail("v29-w@example.test")
            .hireDate(LocalDate.of(2024, 1, 15)).employmentType(EmploymentType.FULL_TIME).build()).getId();
    }

    /** Department, designation and location 990901, two grades and a pay scale (all removed by the test rollback). */
    private void fixtures() {
        jdbc.update("insert into HRMS.DEPARTMENTS (DEPT_ID, CODE, NAME, NAME_AR) values (990901, 'V29T', 'V29 test', N'x')");
        jdbc.update("insert into HRMS.DESIGNATIONS (DESIG_ID, TITLE, CODE, DEPARTMENT_ID) values (990901, 'Clerk', 'V29T_CL', 990901)");
        jdbc.update("""
            insert into HRMS.OFFICE_LOCATIONS (LOCATION_ID, LOCATION_CODE, LOCATION_NAME, LOCATION_NAME_AR, ADDRESS_LINE1, CITY)
            values (990901, 'V29T', 'V29 HQ', N'x', 'Road 1', 'Juba')""");
        jdbc.update("insert into HRMS.GRADES (GRADE_ID, GRADES_CODE, GRADES_NAME, GRADES_NAME_AR, SORT_ORDER, IS_ACTIVE, VERSION) "
            + "values (?, 'V29G', 'V29 grade', N'درجة', 1, 1, 0)", GRADE);
        jdbc.update("insert into HRMS.GRADES (GRADE_ID, GRADES_CODE, GRADES_NAME, GRADES_NAME_AR, SORT_ORDER, IS_ACTIVE, VERSION) "
            + "values (?, 'V29X', 'V29 old grade', N'قديمة', 2, 0, 0)", INACTIVE_GRADE);
        scale(GRADE, 1, "2026-03-01", "460462");
        scale(GRADE, 1, "2027-01-01", "483485");   // a later scale for the same step
        scale(GRADE, 2, "2026-03-01", "483485");
        scale(GRADE, 3, "2027-01-01", "507659");   // a step that only starts later
        scale(INACTIVE_GRADE, 1, "2026-03-01", "100000");
    }

    private void scale(long gradeId, int increment, String from, String amount) {
        jdbc.update("insert into HRMS.SALARY_GRADE_SCALE (GRADE_ID, INCREMENT_NO, EFFECTIVE_FROM, STARTING_SCALE) "
            + "values (?, ?, date '" + from + "', " + amount + ")", gradeId, increment);
    }

    private int job(Long employeeId, Long gradeId, Integer incrementNo) {
        return jdbc.update("""
            insert into HRMS.EMPLOYEE_JOB_DETAILS (JOB_DETAILS_ID, EMPLOYEE_ID, DEPARTMENT_ID, DESIGNATION_ID, LOCATION_ID,
                GRADE_ID, INCREMENT_NO, WORK_MODE, EFFECTIVE_FROM, IS_CURRENT)
            values (HRMS.SEQ_EMPLOYEE_JOB_DETAILS.NEXTVAL, ?, 990901, 990901, 990901, ?, ?, 'ON_SITE', DATE '2024-01-15', 1)
            """, employeeId, gradeId, incrementNo);
    }

    @Test
    void gradeAndIncrementGoTogetherAndNeedAKnownGrade() {
        fixtures();
        Long id = employee();
        assertThrows(DataIntegrityViolationException.class, () -> job(id, GRADE, null));
        assertThrows(DataIntegrityViolationException.class, () -> job(id, null, 1));
        assertThrows(DataIntegrityViolationException.class, () -> job(id, GRADE, 0));
        assertThrows(DataIntegrityViolationException.class, () -> job(id, 999999999L, 1));
        assertEquals(1, job(id, null, null));   // no grade at all is allowed (top posts, contract staff)
    }

    @Test
    void theCurrentJobCarriesTheGradeAndIncrement() {
        fixtures();
        Long id = employee();
        job(id, GRADE, 2);

        JobDetailsProjection p = jobDetailsRepository.findCurrentJobByEmployee(id).orElseThrow();
        assertEquals(GRADE, p.getGradeId());
        assertEquals("V29G", p.getGradeCode());
        assertEquals("V29 grade", p.getGradeName());
        assertEquals("درجة", p.getGradeNameAr());
        assertEquals(2, p.getIncrementNo());
        assertEquals(GRADE, jobDetailsRepository.findJobHistoryByEmployee(id).get(0).getGradeId());
    }

    @Test
    void theScaleOnADateTakesTheLatestRowPerStepAndSkipsInactiveGradesAndLaterSteps() {
        fixtures();
        List<GradeScaleStepProjection> march = gradeRepository.findScaleOn(LocalDate.of(2026, 6, 1)).stream()
            .filter(s -> s.getGradeId() == GRADE || s.getGradeId() == INACTIVE_GRADE).toList();
        assertEquals(List.of(1, 2), march.stream().map(GradeScaleStepProjection::getIncrementNo).toList());
        assertEquals(0, new BigDecimal("460462").compareTo(march.get(0).getStartingScale()));
        assertEquals("2026-03-01", march.get(0).getEffectiveFrom());

        List<GradeScaleStepProjection> next = gradeRepository.findScaleOn(LocalDate.of(2027, 1, 1)).stream()
            .filter(s -> s.getGradeId() == GRADE).toList();
        assertEquals(List.of(1, 2, 3), next.stream().map(GradeScaleStepProjection::getIncrementNo).toList());
        assertEquals(0, new BigDecimal("483485").compareTo(next.get(0).getStartingScale()));
    }

    @Test
    void aStepCountsOnlyForAnActiveGradeAndFromItsEffectiveDate() {
        fixtures();
        LocalDate june = LocalDate.of(2026, 6, 1);
        assertEquals(1, gradeRepository.countScaleSteps(GRADE, 2, june));
        assertEquals(0, gradeRepository.countScaleSteps(GRADE, 3, june));
        assertEquals(0, gradeRepository.countScaleSteps(GRADE, 9, june));
        assertEquals(0, gradeRepository.countScaleSteps(INACTIVE_GRADE, 1, june));
    }
}
