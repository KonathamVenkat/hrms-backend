package com.hrms.employee.repository;

import com.hrms.common.enums.EmploymentStatus;
import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.projection.EmployeeDetailProjection;
import com.hrms.employee.repository.projection.EmployeeListProjection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Employee} entity.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long>,
        JpaSpecificationExecutor<Employee> {

    /**
     * Combined filter query for employee list page.
     *
     * Department and Designation are resolved via EMPLOYEE_JOB_DETAILS (IS_CURRENT=1)
     * so employees added after the job details migration also show correctly.
     *
     * Fallback: if no current job record exists, department/designation will be NULL
     * and the list page shows '—' (handled in Angular template).
     */
    @Query(value = """
            SELECT
                e.EMPLOYEE_ID            AS employeeId,
                e.EMPLOYEE_CODE          AS employeeCode,
                e.FIRST_NAME             AS firstName,
                e.LAST_NAME              AS lastName,
                e.MIDDLE_NAME            AS middleName,
                e.FIRST_NAME_AR          AS firstNameAr,
                e.LAST_NAME_AR           AS lastNameAr,
                e.GENDER                 AS gender,
                e.WORK_EMAIL             AS workEmail,
                e.WORK_PHONE             AS workPhone,
                e.PERSONAL_PHONE         AS personalPhone,
                e.PROFILE_PHOTO_URL      AS profilePhotoUrl,
                e.EMPLOYMENT_STATUS      AS employmentStatus,
                e.EMPLOYMENT_TYPE        AS employmentType,
                e.NATIONALITY            AS nationality,
                TO_CHAR(e.HIRE_DATE, 'YYYY-MM-DD') AS hireDate,
                e.IS_ACTIVE              AS isActive,
                d.DEPT_ID                AS departmentId,
                d.NAME                   AS departmentName,
                d.CODE                   AS departmentCode,
                dsig.DESIG_ID            AS designationId,
                dsig.TITLE               AS designationTitle,
                dsig.GRADE_LEVEL         AS gradeLevel
            FROM HRMS.EMPLOYEES e
            LEFT JOIN HRMS.EMPLOYEE_JOB_DETAILS ejd
                ON ejd.EMPLOYEE_ID = e.EMPLOYEE_ID
               AND ejd.IS_CURRENT  = 1
            LEFT JOIN HRMS.DEPARTMENTS d
                ON d.DEPT_ID    = ejd.DEPARTMENT_ID
            LEFT JOIN HRMS.DESIGNATIONS dsig
                ON dsig.DESIG_ID = ejd.DESIGNATION_ID
            WHERE (:isActive IS NULL OR e.IS_ACTIVE = :isActive)
                AND (
                    :keyword IS NULL OR :keyword = ''
                    OR LOWER(e.FIRST_NAME)    LIKE LOWER('%' || :keyword || '%') ESCAPE '\\'
                    OR LOWER(e.LAST_NAME)     LIKE LOWER('%' || :keyword || '%') ESCAPE '\\'
                    OR LOWER(e.WORK_EMAIL)    LIKE LOWER('%' || :keyword || '%') ESCAPE '\\'
                    OR LOWER(e.EMPLOYEE_CODE) LIKE LOWER('%' || :keyword || '%') ESCAPE '\\'
                )
                AND (:departmentId IS NULL OR ejd.DEPARTMENT_ID = :departmentId)
                AND (:status IS NULL OR e.EMPLOYMENT_STATUS = :status)
                AND (:type   IS NULL OR e.EMPLOYMENT_TYPE   = :type)
                AND (:gender IS NULL OR e.GENDER            = :gender)
            ORDER BY e.EMPLOYEE_CODE
            """,
            countQuery = """
            SELECT COUNT(e.EMPLOYEE_ID)
            FROM HRMS.EMPLOYEES e
            LEFT JOIN HRMS.EMPLOYEE_JOB_DETAILS ejd
                ON ejd.EMPLOYEE_ID = e.EMPLOYEE_ID
               AND ejd.IS_CURRENT  = 1
            WHERE (:isActive IS NULL OR e.IS_ACTIVE = :isActive)
                AND (
                    :keyword IS NULL OR :keyword = ''
                    OR LOWER(e.FIRST_NAME)    LIKE LOWER('%' || :keyword || '%') ESCAPE '\\'
                    OR LOWER(e.LAST_NAME)     LIKE LOWER('%' || :keyword || '%') ESCAPE '\\'
                    OR LOWER(e.WORK_EMAIL)    LIKE LOWER('%' || :keyword || '%') ESCAPE '\\'
                    OR LOWER(e.EMPLOYEE_CODE) LIKE LOWER('%' || :keyword || '%') ESCAPE '\\'
                )
                AND (:departmentId IS NULL OR ejd.DEPARTMENT_ID = :departmentId)
                AND (:status IS NULL OR e.EMPLOYMENT_STATUS = :status)
                AND (:type   IS NULL OR e.EMPLOYMENT_TYPE   = :type)
                AND (:gender IS NULL OR e.GENDER            = :gender)
            """,
            nativeQuery = true)
    Page<EmployeeListProjection> findAllWithFilters(
            @Param("keyword")      String  keyword,
            @Param("departmentId") Long    departmentId,
            @Param("status")       String  status,
            @Param("type")         String  type,
            @Param("gender")       String  gender,
            @Param("isActive")     Integer isActive,
            Pageable                pageable
    );


    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    boolean existsByWorkEmailIgnoreCase(String workEmail);

    boolean existsByPersonalEmailIgnoreCase(String personalEmail);

    boolean existsByEmployeeCodeIgnoreCaseAndIdNot(String employeeCode, Long id);

    boolean existsByWorkEmailIgnoreCaseAndIdNot(String workEmail, Long id);

    boolean existsByPersonalEmailIgnoreCaseAndIdNot(String personalEmail, Long id);

    Optional<Employee> findByEmployeeCodeIgnoreCase(String employeeCode);

    Optional<Employee> findByWorkEmailIgnoreCase(String workEmail);

    Optional<Employee> findByPersonalEmailIgnoreCase(String personalEmail);

    Page<Employee> findAllByIsActive(Boolean isActive, Pageable pageable);

    Optional<Employee> findByIdAndIsActive(Long id, Boolean isActive);

    /**
     * Takes a row lock on the employee until the surrounding transaction ends. Used to serialise
     * changes to one employee's job history so two requests cannot both close and re-open it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e WHERE e.id = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") Long id);

    Page<Employee> findAllByEmploymentStatusAndIsActive(
            EmploymentStatus status, Boolean isActive, Pageable pageable);

    Page<Employee> findAllByEmploymentTypeAndIsActive(
            EmploymentType type, Boolean isActive, Pageable pageable);

    Page<Employee> findAllByGenderAndIsActive(
            Gender gender, Boolean isActive, Pageable pageable);

    // ── Probation management ──────────────────────────────────────────────────

    @Query("""
            SELECT e FROM Employee e
            WHERE e.employmentStatus = 'PROBATION'
              AND e.probationEndDate IS NOT NULL
              AND e.probationEndDate <= :today
              AND e.isActive = true
            """)
    List<Employee> findEmployeesWithExpiredProbation(@Param("today") LocalDate today);

    @Query("""
            SELECT e FROM Employee e
            WHERE e.employmentStatus = 'PROBATION'
              AND e.probationEndDate BETWEEN :today AND :cutoffDate
              AND e.isActive = true
            """)
    List<Employee> findEmployeesWithUpcomingProbationEnd(
            @Param("today") LocalDate today,
            @Param("cutoffDate") LocalDate cutoffDate);

    // ── Reporting & analytics ─────────────────────────────────────────────────

    @Query("""
            SELECT e.employmentStatus, COUNT(e)
            FROM Employee e
            WHERE e.isActive = true
            GROUP BY e.employmentStatus
            """)
    List<Object[]> countByEmploymentStatus();

    @Query("""
            SELECT e.employmentType, COUNT(e)
            FROM Employee e
            WHERE e.isActive = true
            GROUP BY e.employmentType
            """)
    List<Object[]> countByEmploymentType();

    @Query("""
            SELECT e.gender, COUNT(e)
            FROM Employee e
            WHERE e.isActive = true
            GROUP BY e.gender
            """)
    List<Object[]> countByGender();

    @Query("""
            SELECT e FROM Employee e
            WHERE e.hireDate BETWEEN :startDate AND :endDate
              AND e.isActive = :isActive
            ORDER BY e.hireDate DESC
            """)
    List<Employee> findByHireDateBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate,
            @Param("isActive")  Boolean   isActive);

    // ── Dashboard headcount ───────────────────────────────────────────────────

    /** Active employees who have not exited (matches EmploymentStatus.isCurrentlyEmployed()). */
    @Query("""
            SELECT COUNT(e) FROM Employee e
            WHERE e.isActive = true
              AND e.employmentStatus IN (
                  com.hrms.common.enums.EmploymentStatus.ACTIVE,
                  com.hrms.common.enums.EmploymentStatus.PROBATION,
                  com.hrms.common.enums.EmploymentStatus.NOTICE_PERIOD,
                  com.hrms.common.enums.EmploymentStatus.ON_HOLD)
            """)
    long countCurrentlyEmployed();

    long countByHireDateBetweenAndIsActive(LocalDate startDate, LocalDate endDate, Boolean isActive);

    // ── Soft delete / status transition ───────────────────────────────────────

    @Modifying
    @Query("""
        UPDATE Employee e
        SET e.isActive = false,
            e.updatedBy = :updatedBy
        WHERE e.id = :id
        """)
    int softDeleteById(
        @Param("id") Long id,
        @Param("updatedBy") String updatedBy
    );

    @Modifying
    @Query("""
            UPDATE Employee e
            SET e.employmentStatus = :newStatus, e.updatedBy = :updatedBy
            WHERE e.id IN :ids AND e.isActive = true
            """)
    int bulkUpdateStatus(
            @Param("ids")       List<Long>       ids,
            @Param("newStatus") EmploymentStatus newStatus,
            @Param("updatedBy") String           updatedBy);

    // ── Nationality filter ────────────────────────────────────────────────────

    Page<Employee> findAllByNationalityIgnoreCaseAndIsActive(
            String nationality, Boolean isActive, Pageable pageable);

    // ── Full employee detail with job details ─────────────────────────────────

    /**
     * Fetches full employee detail, including deactivated employees (HR must be able to open
     * them to reactivate); {@code isActive} in the result tells the caller which it is.
     * Department and Designation resolved via EMPLOYEE_JOB_DETAILS (IS_CURRENT=1)
     * so the detail page always shows the latest job assignment.
     */
    @Query(value = """
        SELECT
            e.EMPLOYEE_ID                             AS employeeId,
            e.EMPLOYEE_CODE                           AS employeeCode,
            e.FIRST_NAME                              AS firstName,
            e.FIRST_NAME_AR                           AS firstNameAr,
            e.MIDDLE_NAME                             AS middleName,
            e.MIDDLE_NAME_AR                          AS middleNameAr,
            e.LAST_NAME                               AS lastName,
            e.LAST_NAME_AR                            AS lastNameAr,
            TO_CHAR(e.DATE_OF_BIRTH,  'YYYY-MM-DD')  AS dateOfBirth,
            e.GENDER                                  AS gender,
            e.BLOOD_GROUP                             AS bloodGroup,
            e.MARITAL_STATUS                          AS maritalStatus,
            e.NATIONALITY                             AS nationality,
            e.RELIGION                                AS religion,
            e.PROFILE_PHOTO_URL                       AS profilePhotoUrl,
            e.PERSONAL_EMAIL                          AS personalEmail,
            e.WORK_EMAIL                              AS workEmail,
            e.PERSONAL_PHONE                          AS personalPhone,
            e.WORK_PHONE                              AS workPhone,
            TO_CHAR(e.HIRE_DATE,         'YYYY-MM-DD') AS hireDate,
            TO_CHAR(e.PROBATION_END_DATE,'YYYY-MM-DD') AS probationEndDate,
            TO_CHAR(e.CONFIRMATION_DATE, 'YYYY-MM-DD') AS confirmationDate,
            e.EMPLOYMENT_STATUS                       AS employmentStatus,
            e.EMPLOYMENT_TYPE                         AS employmentType,
            e.IS_ACTIVE                               AS isActive,
            e.CREATED_BY                              AS createdBy,
            TO_CHAR(e.CREATED_AT, 'YYYY-MM-DD HH24:MI:SS') AS createdAt,
            e.UPDATED_BY                              AS updatedBy,
            TO_CHAR(e.UPDATED_AT, 'YYYY-MM-DD HH24:MI:SS') AS updatedAt,
            d.DEPT_ID                                 AS departmentId,
            d.NAME                                    AS departmentName,
            d.CODE                                    AS departmentCode,
            d.NAME_AR                                 AS departmentNameAr,
            dsig.DESIG_ID                             AS designationId,
            dsig.TITLE                                AS designationTitle,
            dsig.TITLE_AR                             AS designationTitleAr,
            dsig.CODE                                 AS designationCode,
            dsig.GRADE_LEVEL                          AS gradeLevel
        FROM HRMS.EMPLOYEES e
        LEFT JOIN HRMS.EMPLOYEE_JOB_DETAILS ejd
            ON ejd.EMPLOYEE_ID = e.EMPLOYEE_ID
           AND ejd.IS_CURRENT  = 1
        LEFT JOIN HRMS.DEPARTMENTS d
            ON d.DEPT_ID    = ejd.DEPARTMENT_ID
        LEFT JOIN HRMS.DESIGNATIONS dsig
            ON dsig.DESIG_ID = ejd.DESIGNATION_ID
        WHERE
            e.EMPLOYEE_ID = :id
        """,
        nativeQuery = true)
    Optional<EmployeeDetailProjection> findDetailById(@Param("id") Long id);
}