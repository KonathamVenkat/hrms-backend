package com.hrms.employee.repository;

import com.hrms.employee.entity.Grade;
import com.hrms.employee.repository.projection.GradeScaleStepProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface GradeRepository extends JpaRepository<Grade, Long> {
    List<Grade> findAllByOrderBySortOrderAscNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /**
     * The pay scale on a date: for every increment of every active grade, the row with the latest EFFECTIVE_FROM on or
     * before that date. A few dozen rows (13 grades); served by the pay scale's primary key (GRADE_ID first).
     */
    @Query(value = """
        SELECT g.GRADE_ID       AS gradeId,
               g.GRADES_CODE    AS gradeCode,
               g.GRADES_NAME    AS gradeName,
               g.GRADES_NAME_AR AS gradeNameAr,
               s.INCREMENT_NO   AS incrementNo,
               s.STARTING_SCALE AS startingScale,
               TO_CHAR(s.EFFECTIVE_FROM, 'YYYY-MM-DD') AS effectiveFrom
        FROM HRMS.SALARY_GRADE_SCALE s
        JOIN HRMS.GRADES g ON g.GRADE_ID = s.GRADE_ID
        WHERE g.IS_ACTIVE = 1
          AND s.EFFECTIVE_FROM = (SELECT MAX(s2.EFFECTIVE_FROM) FROM HRMS.SALARY_GRADE_SCALE s2
                                   WHERE s2.GRADE_ID = s.GRADE_ID AND s2.INCREMENT_NO = s.INCREMENT_NO
                                     AND s2.EFFECTIVE_FROM <= :onDate)
        ORDER BY g.SORT_ORDER NULLS LAST, g.GRADES_NAME, s.INCREMENT_NO
        """, nativeQuery = true)
    List<GradeScaleStepProjection> findScaleOn(@Param("onDate") LocalDate onDate);

    /** True when the grade is active and the pay scale has this increment for it in effect on the date. */
    @Query(value = """
        SELECT COUNT(*)
        FROM HRMS.SALARY_GRADE_SCALE s
        JOIN HRMS.GRADES g ON g.GRADE_ID = s.GRADE_ID
        WHERE s.GRADE_ID = :gradeId AND s.INCREMENT_NO = :incrementNo
          AND s.EFFECTIVE_FROM <= :onDate AND g.IS_ACTIVE = 1
        """, nativeQuery = true)
    long countScaleSteps(@Param("gradeId") Long gradeId, @Param("incrementNo") Integer incrementNo,
                         @Param("onDate") LocalDate onDate);
}
