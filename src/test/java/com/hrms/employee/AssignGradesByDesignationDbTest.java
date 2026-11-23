package com.hrms.employee;

import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * db/assign_grades_by_designation.sql (and its rollback) on a real Oracle schema: grade from the designation, increment from
 * years of service on 2026-11-23, top posts and already-graded rows untouched, a pair missing from the pay scale skipped, a
 * second run changes nothing. The scripts' COMMIT is left out so the test transaction rolls everything back.
 */
@Transactional
class AssignGradesByDesignationDbTest extends DatabaseTest {

    private static final long DEPT = 990951;

    @Autowired JdbcTemplate jdbc;
    @Autowired EmployeeRepository employeeRepository;

    private int run(String file) throws Exception {
        String text = Files.readString(Path.of(file)).replaceAll("(?m)^--.*$", "");
        return Arrays.stream(text.split(";\\s*\\n")).map(String::trim)
            .filter(s -> !s.isEmpty() && !s.equalsIgnoreCase("COMMIT"))
            .mapToInt(jdbc::update).sum();
    }

    private void grade(String code, int sort, boolean withScale) {
        jdbc.update("""
            insert into HRMS.GRADES (GRADE_ID, GRADES_CODE, GRADES_NAME, GRADES_NAME_AR, SORT_ORDER, IS_ACTIVE, VERSION)
            select ?, ?, ?, N'x', ?, 1, 0 from dual where not exists (select 1 from HRMS.GRADES where GRADES_CODE = ?)""",
            DEPT + sort, code, "Grade " + sort, sort, code);
        jdbc.update("delete from HRMS.SALARY_GRADE_SCALE where GRADE_ID = (select GRADE_ID from HRMS.GRADES where GRADES_CODE = ?)", code);
        if (withScale) {
            for (int inc = 1; inc <= 4; inc++) {
                jdbc.update("insert into HRMS.SALARY_GRADE_SCALE (GRADE_ID, INCREMENT_NO, EFFECTIVE_FROM, STARTING_SCALE) "
                    + "select GRADE_ID, ?, date '2026-03-01', ? from HRMS.GRADES where GRADES_CODE = ?", inc, 100000 * sort + inc, code);
            }
        }
    }

    private long gradeId(String code) {
        Long id = jdbc.queryForObject("select GRADE_ID from HRMS.GRADES where GRADES_CODE = ?", Long.class, code);
        return id == null ? -1 : id;
    }

    private long person(String key, LocalDate hired, String designationCode, Long gradeId, Integer inc, int current) {
        Long id = employeeRepository.saveAndFlush(Employee.builder()
            .firstName("Agd" + key).firstNameAr("ع").lastName("Test").lastNameAr("ت")
            .dateOfBirth(LocalDate.of(1985, 1, 1)).gender(Gender.MALE)
            .personalEmail("agd-p-" + key + "@example.test").workEmail("agd-w-" + key + "@example.test")
            .hireDate(hired).employmentType(EmploymentType.FULL_TIME).build()).getId();
        jdbc.update("""
            insert into HRMS.EMPLOYEE_JOB_DETAILS (JOB_DETAILS_ID, EMPLOYEE_ID, DEPARTMENT_ID, DESIGNATION_ID, LOCATION_ID,
                GRADE_ID, INCREMENT_NO, WORK_MODE, EFFECTIVE_FROM, IS_CURRENT)
            values (HRMS.SEQ_EMPLOYEE_JOB_DETAILS.NEXTVAL, ?, ?, (select DESIG_ID from HRMS.DESIGNATIONS where CODE = ?), ?,
                    ?, ?, 'ON_SITE', DATE '2024-01-15', ?)""",
            new Object[] {id, DEPT, designationCode, DEPT, gradeId, inc, current},
            new int[] {Types.NUMERIC, Types.NUMERIC, Types.VARCHAR, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC});
        return id;
    }

    private Map<String, Object> job(long employeeId) {
        return jdbc.queryForMap("""
            select g.GRADES_CODE, j.INCREMENT_NO, j.UPDATED_BY from HRMS.EMPLOYEE_JOB_DETAILS j
            left join HRMS.GRADES g on g.GRADE_ID = j.GRADE_ID where j.EMPLOYEE_ID = ?""", employeeId);
    }

    @Test
    void gradesFromDesignationIncrementsFromServiceSkipsTheRest() throws Exception {
        jdbc.update("insert into HRMS.DEPARTMENTS (DEPT_ID, CODE, NAME, NAME_AR) values (?, 'AGDT', 'Agd test', N'x')", DEPT);
        jdbc.update("""
            insert into HRMS.OFFICE_LOCATIONS (LOCATION_ID, LOCATION_CODE, LOCATION_NAME, LOCATION_NAME_AR, ADDRESS_LINE1, CITY)
            values (?, 'AGDT', 'Agd HQ', N'x', 'Road 1', 'Juba')""", DEPT);
        String[][] designations = {{"SDH-AGDT-HEAD", "Head of Department"}, {"SDH-AGDT-SEC", "Department Secretary"},
            {"AGDT_SE", "Software Engineer"}, {"AGDT_CEO", "Chief Executive Officer"}, {"AGDT_ITM", "IT Manager"},
            {"AGDT_SA", "Senior Accountant"}};
        for (String[] d : designations) {
            jdbc.update("insert into HRMS.DESIGNATIONS (DESIG_ID, TITLE, CODE, DEPARTMENT_ID) values (HRMS.SEQ_DESIGNATIONS.NEXTVAL, ?, ?, ?)",
                d[1], d[0], DEPT);
        }
        grade("G03", 3, true);
        grade("G05", 5, false);   // no pay scale: Senior Accountant must be skipped
        grade("G07", 7, true);
        grade("G10", 10, true);

        long head = person("head", LocalDate.of(2015, 3, 1), "SDH-AGDT-HEAD", null, null, 1);       // 11 years -> 4
        long sec = person("sec", LocalDate.of(2025, 1, 10), "SDH-AGDT-SEC", null, null, 1);         // 1 year   -> 1
        long eng = person("eng", LocalDate.of(2021, 7, 5), "AGDT_SE", null, null, 1);               // 5 years  -> 2
        long edge = person("edge", LocalDate.of(2020, 11, 24), "AGDT_SE", null, null, 1);           // 5 y 364 d -> 2
        long six = person("six", LocalDate.of(2020, 11, 23), "AGDT_SE", null, null, 1);             // exactly 6 -> 3
        long ceo = person("ceo", LocalDate.of(2012, 3, 1), "AGDT_CEO", null, null, 1);              // top post: none
        long itm = person("itm", LocalDate.of(2016, 1, 5), "AGDT_ITM", gradeId("G07"), 1, 1);       // already graded
        long sa = person("sa", LocalDate.of(2023, 3, 10), "AGDT_SA", null, null, 1);                // no scale: skip
        long old = person("old", LocalDate.of(2010, 1, 1), "AGDT_SE", null, null, 0);               // not current

        assertEquals(5, run("db/assign_grades_by_designation.sql"));
        assertEquals(Map.of("GRADES_CODE", "G03", "INCREMENT_NO", 4, "UPDATED_BY", "GRADEMAP"), norm(job(head)));
        assertEquals(Map.of("GRADES_CODE", "G10", "INCREMENT_NO", 1, "UPDATED_BY", "GRADEMAP"), norm(job(sec)));
        assertEquals(Map.of("GRADES_CODE", "G07", "INCREMENT_NO", 2, "UPDATED_BY", "GRADEMAP"), norm(job(eng)));
        assertEquals(2, norm(job(edge)).get("INCREMENT_NO"));
        assertEquals(3, norm(job(six)).get("INCREMENT_NO"));
        assertNull(job(ceo).get("GRADES_CODE"));
        assertEquals("G07", job(itm).get("GRADES_CODE"));
        assertNull(job(itm).get("UPDATED_BY"));
        assertNull(job(sa).get("GRADES_CODE"));
        assertNull(job(old).get("GRADES_CODE"));

        assertEquals(0, run("db/assign_grades_by_designation.sql"));   // second run: nothing left to grade

        assertEquals(5, run("db/assign_grades_by_designation_rollback.sql"));
        assertNull(job(head).get("GRADES_CODE"));
        assertNull(job(eng).get("INCREMENT_NO"));
        assertEquals("G07", job(itm).get("GRADES_CODE"));   // rows it did not set stay
    }

    private static Map<String, Object> norm(Map<String, Object> row) {
        return Map.of("GRADES_CODE", row.get("GRADES_CODE"),
            "INCREMENT_NO", ((Number) row.get("INCREMENT_NO")).intValue(), "UPDATED_BY", row.get("UPDATED_BY"));
    }
}
