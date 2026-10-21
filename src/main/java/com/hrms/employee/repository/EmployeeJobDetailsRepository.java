package com.hrms.employee.repository;

import com.hrms.employee.dto.response.ManagerLookupResponse;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.repository.projection.EmployeeBranchGapProjection;
import com.hrms.employee.repository.projection.JobDetailsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    boolean existsByDepartmentId(Long departmentId);
    boolean existsByDesignationId(Long designationId);

    /** Active employees whose current job row names this person as reporting or functional manager. */
    @Query("""
        SELECT COUNT(j) FROM EmployeeJobDetails j, Employee e
        WHERE e.id = j.employeeId AND e.isActive = true AND j.isCurrent = 1
          AND (j.reportingManagerId = :managerId OR j.functionalManagerId = :managerId)
        """)
    long countActiveReportsOf(@Param("managerId") Long managerId);

    /**
     * Active employees whose CURRENT job is in the department and whose designation is manager or
     * executive grade (M*, E*): the people who can be reporting or functional manager there.
     */
    @Query("""
        SELECT new com.hrms.employee.dto.response.ManagerLookupResponse(
               e.id, e.employeeCode, e.firstName, e.middleName, e.lastName, d.title, d.gradeLevel)
        FROM EmployeeJobDetails j, Employee e, Designation d
        WHERE e.id = j.employeeId AND d.desigId = j.designationId
          AND j.isCurrent = 1 AND j.departmentId = :departmentId
          AND e.isActive = true
          AND (d.gradeLevel LIKE 'M%' OR d.gradeLevel LIKE 'E%')
        ORDER BY d.gradeLevel, e.firstName, e.lastName
        """)
    List<ManagerLookupResponse> findManagerCandidates(@Param("departmentId") Long departmentId);

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
            ejd.CREATED_BY            AS createdBy,
            ejd.COMPANY_BRANCH_ID     AS companyBranchId,
            cb.COMPANY_BRANCHES_CODE  AS branchCode,
            cb.COMPANY_BRANCHES_NAME  AS branchName,
            cb.COMPANY_BRANCHES_NAME_AR AS branchNameAr,
            TRIM(bm.FIRST_NAME || ' ' || bm.LAST_NAME) AS branchManagerName
        FROM HRMS.EMPLOYEE_JOB_DETAILS ejd
        LEFT JOIN HRMS.DEPARTMENTS     d   ON d.DEPT_ID       = ejd.DEPARTMENT_ID
        LEFT JOIN HRMS.DESIGNATIONS    ds  ON ds.DESIG_ID     = ejd.DESIGNATION_ID
        LEFT JOIN HRMS.EMPLOYEES       rm  ON rm.EMPLOYEE_ID  = ejd.REPORTING_MANAGER_ID
        LEFT JOIN HRMS.EMPLOYEES       fm  ON fm.EMPLOYEE_ID  = ejd.FUNCTIONAL_MANAGER_ID
        LEFT JOIN HRMS.OFFICE_LOCATIONS loc ON loc.LOCATION_ID = ejd.LOCATION_ID
        LEFT JOIN HRMS.WORK_SHIFTS     ws  ON ws.SHIFT_ID     = ejd.SHIFT_ID
        LEFT JOIN HRMS.COMPANY_BRANCHES cb ON cb.COMPANY_BRANCH_ID = ejd.COMPANY_BRANCH_ID
        LEFT JOIN HRMS.EMPLOYEES       bm  ON bm.EMPLOYEE_ID  = cb.BRANCH_MANAGER_EMPLOYEE_ID
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
            ejd.CREATED_BY            AS createdBy,
            ejd.COMPANY_BRANCH_ID     AS companyBranchId,
            cb.COMPANY_BRANCHES_CODE  AS branchCode,
            cb.COMPANY_BRANCHES_NAME  AS branchName,
            cb.COMPANY_BRANCHES_NAME_AR AS branchNameAr,
            TRIM(bm.FIRST_NAME || ' ' || bm.LAST_NAME) AS branchManagerName
        FROM HRMS.EMPLOYEE_JOB_DETAILS ejd
        LEFT JOIN HRMS.DEPARTMENTS     d   ON d.DEPT_ID       = ejd.DEPARTMENT_ID
        LEFT JOIN HRMS.DESIGNATIONS    ds  ON ds.DESIG_ID     = ejd.DESIGNATION_ID
        LEFT JOIN HRMS.EMPLOYEES       rm  ON rm.EMPLOYEE_ID  = ejd.REPORTING_MANAGER_ID
        LEFT JOIN HRMS.EMPLOYEES       fm  ON fm.EMPLOYEE_ID  = ejd.FUNCTIONAL_MANAGER_ID
        LEFT JOIN HRMS.OFFICE_LOCATIONS loc ON loc.LOCATION_ID = ejd.LOCATION_ID
        LEFT JOIN HRMS.WORK_SHIFTS     ws  ON ws.SHIFT_ID     = ejd.SHIFT_ID
        LEFT JOIN HRMS.COMPANY_BRANCHES cb ON cb.COMPANY_BRANCH_ID = ejd.COMPANY_BRANCH_ID
        LEFT JOIN HRMS.EMPLOYEES       bm  ON bm.EMPLOYEE_ID  = cb.BRANCH_MANAGER_EMPLOYEE_ID
        WHERE ejd.EMPLOYEE_ID = :employeeId
          AND ejd.IS_CURRENT  = 1
        """,
        nativeQuery = true)
    Optional<JobDetailsProjection> findCurrentJobByEmployee(
            @Param("employeeId") Long employeeId);

    // ── Branch back-fill (workflow phase 1) ────────────────────

    /** Active employees whose current job row has no branch; the list is fixed to employee-code order. */
    @Query(value = """
        SELECT e.EMPLOYEE_ID      AS employeeId,
               e.EMPLOYEE_CODE    AS employeeCode,
               e.FIRST_NAME       AS firstName,
               e.LAST_NAME        AS lastName,
               e.FIRST_NAME_AR    AS firstNameAr,
               e.LAST_NAME_AR     AS lastNameAr,
               d.NAME             AS departmentName,
               ejd.LOCATION_ID    AS locationId,
               loc.LOCATION_NAME  AS locationName
        FROM HRMS.EMPLOYEES e
        JOIN HRMS.EMPLOYEE_JOB_DETAILS ejd ON ejd.EMPLOYEE_ID = e.EMPLOYEE_ID AND ejd.IS_CURRENT = 1
        LEFT JOIN HRMS.DEPARTMENTS d ON d.DEPT_ID = ejd.DEPARTMENT_ID
        LEFT JOIN HRMS.OFFICE_LOCATIONS loc ON loc.LOCATION_ID = ejd.LOCATION_ID
        WHERE e.IS_ACTIVE = 1
          AND ejd.COMPANY_BRANCH_ID IS NULL
          AND (:keyword IS NULL
               OR LOWER(e.FIRST_NAME)    LIKE LOWER('%' || :keyword || '%') ESCAPE '\\'
               OR LOWER(e.LAST_NAME)     LIKE LOWER('%' || :keyword || '%') ESCAPE '\\'
               OR LOWER(e.EMPLOYEE_CODE) LIKE LOWER('%' || :keyword || '%') ESCAPE '\\')
        ORDER BY e.EMPLOYEE_CODE
        """,
        countQuery = """
        SELECT COUNT(*)
        FROM HRMS.EMPLOYEES e
        JOIN HRMS.EMPLOYEE_JOB_DETAILS ejd ON ejd.EMPLOYEE_ID = e.EMPLOYEE_ID AND ejd.IS_CURRENT = 1
        WHERE e.IS_ACTIVE = 1
          AND ejd.COMPANY_BRANCH_ID IS NULL
          AND (:keyword IS NULL
               OR LOWER(e.FIRST_NAME)    LIKE LOWER('%' || :keyword || '%') ESCAPE '\\'
               OR LOWER(e.LAST_NAME)     LIKE LOWER('%' || :keyword || '%') ESCAPE '\\'
               OR LOWER(e.EMPLOYEE_CODE) LIKE LOWER('%' || :keyword || '%') ESCAPE '\\')
        """,
        nativeQuery = true)
    Page<EmployeeBranchGapProjection> findEmployeesWithoutBranch(@Param("keyword") String keyword, Pageable pageable);

    @Query(value = """
        SELECT COUNT(*)
        FROM HRMS.EMPLOYEES e
        JOIN HRMS.EMPLOYEE_JOB_DETAILS ejd ON ejd.EMPLOYEE_ID = e.EMPLOYEE_ID AND ejd.IS_CURRENT = 1
        WHERE e.IS_ACTIVE = 1 AND ejd.COMPANY_BRANCH_ID IS NULL
        """, nativeQuery = true)
    long countEmployeesWithoutBranch();

    /** Branch-manager candidates: active employees whose current job is at the location (null = anywhere). */
    @Query(value = """
        SELECT e.EMPLOYEE_ID      AS employeeId,
               e.EMPLOYEE_CODE    AS employeeCode,
               e.FIRST_NAME       AS firstName,
               e.LAST_NAME        AS lastName,
               e.FIRST_NAME_AR    AS firstNameAr,
               e.LAST_NAME_AR     AS lastNameAr,
               d.NAME             AS departmentName,
               ejd.LOCATION_ID    AS locationId,
               loc.LOCATION_NAME  AS locationName
        FROM HRMS.EMPLOYEES e
        JOIN HRMS.EMPLOYEE_JOB_DETAILS ejd ON ejd.EMPLOYEE_ID = e.EMPLOYEE_ID AND ejd.IS_CURRENT = 1
        LEFT JOIN HRMS.DEPARTMENTS d ON d.DEPT_ID = ejd.DEPARTMENT_ID
        LEFT JOIN HRMS.OFFICE_LOCATIONS loc ON loc.LOCATION_ID = ejd.LOCATION_ID
        WHERE e.IS_ACTIVE = 1
          AND (:locationId IS NULL OR ejd.LOCATION_ID = :locationId)
          AND (:keyword IS NULL
               OR LOWER(e.FIRST_NAME)    LIKE LOWER('%' || :keyword || '%') ESCAPE '\\'
               OR LOWER(e.LAST_NAME)     LIKE LOWER('%' || :keyword || '%') ESCAPE '\\'
               OR LOWER(e.EMPLOYEE_CODE) LIKE LOWER('%' || :keyword || '%') ESCAPE '\\')
        ORDER BY e.FIRST_NAME, e.LAST_NAME, e.EMPLOYEE_CODE
        """,
        countQuery = """
        SELECT COUNT(*)
        FROM HRMS.EMPLOYEES e
        JOIN HRMS.EMPLOYEE_JOB_DETAILS ejd ON ejd.EMPLOYEE_ID = e.EMPLOYEE_ID AND ejd.IS_CURRENT = 1
        WHERE e.IS_ACTIVE = 1
          AND (:locationId IS NULL OR ejd.LOCATION_ID = :locationId)
          AND (:keyword IS NULL
               OR LOWER(e.FIRST_NAME)    LIKE LOWER('%' || :keyword || '%') ESCAPE '\\'
               OR LOWER(e.LAST_NAME)     LIKE LOWER('%' || :keyword || '%') ESCAPE '\\'
               OR LOWER(e.EMPLOYEE_CODE) LIKE LOWER('%' || :keyword || '%') ESCAPE '\\')
        """,
        nativeQuery = true)
    Page<EmployeeBranchGapProjection> findManagerCandidates(@Param("locationId") Long locationId,
                                                            @Param("keyword") String keyword, Pageable pageable);

    /**
     * Back-fill correction: sets the branch on the current job row only while it still has none, so two HR users
     * (or a double click) cannot overwrite each other. Returns 0 when the row already had a branch.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE EmployeeJobDetails ejd
        SET ejd.companyBranchId = :branchId,
            ejd.updatedBy       = :updatedBy,
            ejd.updatedAt       = CURRENT_TIMESTAMP
        WHERE ejd.employeeId = :employeeId
          AND ejd.isCurrent  = 1
          AND ejd.companyBranchId IS NULL
        """)
    int setBranchOnCurrentJob(@Param("employeeId") Long employeeId,
                              @Param("branchId") Long branchId,
                              @Param("updatedBy") String updatedBy);

    /** Active employees whose current job row names this branch (moving the branch would strand them). */
    @Query("""
        SELECT COUNT(j) FROM EmployeeJobDetails j
        WHERE j.companyBranchId = :branchId
          AND j.isCurrent = 1
          AND EXISTS (SELECT 1 FROM Employee e WHERE e.id = j.employeeId AND e.isActive = true)
        """)
    long countCurrentJobsInBranch(@Param("branchId") Long branchId);

    // ── Count historical records ───────────────────────────────
    long countByEmployeeId(Long employeeId);
}
