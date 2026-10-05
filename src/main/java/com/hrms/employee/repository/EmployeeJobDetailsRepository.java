package com.hrms.employee.repository;

import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.repository.projection.JobDetailsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeJobDetailsRepository
        extends JpaRepository<EmployeeJobDetails, Long> {

    /** Active employees whose current job row names this person as reporting or functional manager. */
    @Query("""
        SELECT COUNT(j) FROM EmployeeJobDetails j, Employee e
        WHERE e.id = j.employeeId AND e.isActive = true AND j.isCurrent = 1
          AND (j.reportingManagerId = :managerId OR j.functionalManagerId = :managerId)
        """)
    long countActiveReportsOf(@Param("managerId") Long managerId);

    // ── Current job record for an employee ────────────────────

    Optional<EmployeeJobDetails> findByEmployeeIdAndIsCurrent(
            Long employeeId, Integer isCurrent);

    // ── Full job history for an employee ──────────────────────

    List<EmployeeJobDetails> findByEmployeeIdOrderByEffectiveFromDesc(
            Long employeeId);

    // ── Close the current record before inserting a new one ───

    @Modifying
    @Query("""
        UPDATE EmployeeJobDetails ejd
        SET ejd.isCurrent   = 0,
            ejd.effectiveTo = :effectiveTo,
            ejd.updatedBy   = :updatedBy,
            ejd.updatedAt   = CURRENT_TIMESTAMP
        WHERE ejd.employeeId = :employeeId
          AND ejd.isCurrent  = 1
        """)
    int closeCurrentRecord(
            @Param("employeeId")  Long      employeeId,
            @Param("effectiveTo") LocalDate effectiveTo,
            @Param("updatedBy")   String    updatedBy
    );

    // ── Native query — full detail with all joined names ──────

    @Query(value = """
        SELECT
            ejd.JOB_DETAILS_ID        AS jobDetailsId,
            ejd.EMPLOYEE_ID           AS employeeId,
            ejd.DEPARTMENT_ID         AS departmentId,
            d.NAME                    AS departmentName,
            d.NAME_AR                 AS departmentNameAr,
            d.CODE                    AS departmentCode,
            ejd.DESIGNATION_ID        AS designationId,
            ds.TITLE                  AS designationTitle,
            ds.TITLE_AR               AS designationTitleAr,
            ds.GRADE_LEVEL            AS gradeLevel,
            ejd.JOB_POSITION_ID       AS jobPositionId,
            ejd.REPORTING_MANAGER_ID  AS reportingManagerId,
            rm.FIRST_NAME || ' ' || rm.LAST_NAME AS reportingManagerName,
            rm.EMPLOYEE_CODE          AS reportingManagerCode,
            ejd.FUNCTIONAL_MANAGER_ID AS functionalManagerId,
            fm.FIRST_NAME || ' ' || fm.LAST_NAME AS functionalManagerName,
            fm.EMPLOYEE_CODE          AS functionalManagerCode,
            ejd.LOCATION_ID           AS locationId,
            loc.LOCATION_NAME         AS locationName,
            loc.LOCATION_NAME_AR      AS locationNameAr,
            loc.LOCATION_CODE         AS locationCode,
            loc.CITY                  AS locationCity,
            ejd.SHIFT_ID              AS shiftId,
            ws.SHIFT_NAME             AS shiftName,
            ws.START_TIME             AS shiftStartTime,
            ws.END_TIME               AS shiftEndTime,
            ejd.WORK_MODE             AS workMode,
            TO_CHAR(ejd.EFFECTIVE_FROM,'YYYY-MM-DD') AS effectiveFrom,
            TO_CHAR(ejd.EFFECTIVE_TO, 'YYYY-MM-DD')  AS effectiveTo,
            ejd.IS_CURRENT            AS isCurrent,
            ejd.REMARKS               AS remarks,
            TO_CHAR(ejd.CREATED_AT,'YYYY-MM-DD HH24:MI:SS') AS createdAt,
            ejd.CREATED_BY            AS createdBy
        FROM HRMS.EMPLOYEE_JOB_DETAILS ejd
        LEFT JOIN HRMS.DEPARTMENTS     d   ON d.DEPT_ID       = ejd.DEPARTMENT_ID
        LEFT JOIN HRMS.DESIGNATIONS    ds  ON ds.DESIG_ID     = ejd.DESIGNATION_ID
        LEFT JOIN HRMS.EMPLOYEES       rm  ON rm.EMPLOYEE_ID  = ejd.REPORTING_MANAGER_ID
        LEFT JOIN HRMS.EMPLOYEES       fm  ON fm.EMPLOYEE_ID  = ejd.FUNCTIONAL_MANAGER_ID
        LEFT JOIN HRMS.OFFICE_LOCATIONS loc ON loc.LOCATION_ID = ejd.LOCATION_ID
        LEFT JOIN HRMS.WORK_SHIFTS     ws  ON ws.SHIFT_ID     = ejd.SHIFT_ID
        WHERE ejd.EMPLOYEE_ID = :employeeId
        ORDER BY ejd.EFFECTIVE_FROM DESC
        """,
        nativeQuery = true)
    List<JobDetailsProjection> findJobHistoryByEmployee(
            @Param("employeeId") Long employeeId);

    @Query(value = """
        SELECT
            ejd.JOB_DETAILS_ID        AS jobDetailsId,
            ejd.EMPLOYEE_ID           AS employeeId,
            ejd.DEPARTMENT_ID         AS departmentId,
            d.NAME                    AS departmentName,
            d.NAME_AR                 AS departmentNameAr,
            d.CODE                    AS departmentCode,
            ejd.DESIGNATION_ID        AS designationId,
            ds.TITLE                  AS designationTitle,
            ds.TITLE_AR               AS designationTitleAr,
            ds.GRADE_LEVEL            AS gradeLevel,
            ejd.JOB_POSITION_ID       AS jobPositionId,
            ejd.REPORTING_MANAGER_ID  AS reportingManagerId,
            rm.FIRST_NAME || ' ' || rm.LAST_NAME AS reportingManagerName,
            rm.EMPLOYEE_CODE          AS reportingManagerCode,
            ejd.FUNCTIONAL_MANAGER_ID AS functionalManagerId,
            fm.FIRST_NAME || ' ' || fm.LAST_NAME AS functionalManagerName,
            fm.EMPLOYEE_CODE          AS functionalManagerCode,
            ejd.LOCATION_ID           AS locationId,
            loc.LOCATION_NAME         AS locationName,
            loc.LOCATION_NAME_AR      AS locationNameAr,
            loc.LOCATION_CODE         AS locationCode,
            loc.CITY                  AS locationCity,
            ejd.SHIFT_ID              AS shiftId,
            ws.SHIFT_NAME             AS shiftName,
            ws.START_TIME             AS shiftStartTime,
            ws.END_TIME               AS shiftEndTime,
            ejd.WORK_MODE             AS workMode,
            TO_CHAR(ejd.EFFECTIVE_FROM,'YYYY-MM-DD') AS effectiveFrom,
            TO_CHAR(ejd.EFFECTIVE_TO, 'YYYY-MM-DD')  AS effectiveTo,
            ejd.IS_CURRENT            AS isCurrent,
            ejd.REMARKS               AS remarks,
            TO_CHAR(ejd.CREATED_AT,'YYYY-MM-DD HH24:MI:SS') AS createdAt,
            ejd.CREATED_BY            AS createdBy
        FROM HRMS.EMPLOYEE_JOB_DETAILS ejd
        LEFT JOIN HRMS.DEPARTMENTS     d   ON d.DEPT_ID       = ejd.DEPARTMENT_ID
        LEFT JOIN HRMS.DESIGNATIONS    ds  ON ds.DESIG_ID     = ejd.DESIGNATION_ID
        LEFT JOIN HRMS.EMPLOYEES       rm  ON rm.EMPLOYEE_ID  = ejd.REPORTING_MANAGER_ID
        LEFT JOIN HRMS.EMPLOYEES       fm  ON fm.EMPLOYEE_ID  = ejd.FUNCTIONAL_MANAGER_ID
        LEFT JOIN HRMS.OFFICE_LOCATIONS loc ON loc.LOCATION_ID = ejd.LOCATION_ID
        LEFT JOIN HRMS.WORK_SHIFTS     ws  ON ws.SHIFT_ID     = ejd.SHIFT_ID
        WHERE ejd.EMPLOYEE_ID = :employeeId
          AND ejd.IS_CURRENT  = 1
        """,
        nativeQuery = true)
    Optional<JobDetailsProjection> findCurrentJobByEmployee(
            @Param("employeeId") Long employeeId);

    // ── Count historical records ───────────────────────────────
    long countByEmployeeId(Long employeeId);
}
