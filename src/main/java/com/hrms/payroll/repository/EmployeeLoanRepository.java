package com.hrms.payroll.repository;

import com.hrms.payroll.entity.EmployeeLoan;
import com.hrms.payroll.repository.projection.EmployeeLoanRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Repository
public interface EmployeeLoanRepository extends JpaRepository<EmployeeLoan, Long> {

    @Query(value = "SELECT HRMS.SEQ_EMPLOYEE_LOANS.NEXTVAL FROM DUAL", nativeQuery = true)
    Long findNextSequenceValue();

    boolean existsByEmployeeIdAndComponentIdAndStatus(Long employeeId, Long componentId, EmployeeLoan.LoanStatus status);

    /**
     * The register, newest first, with the employee's and the loan type's names. Filters are optional: a text found in
     * the employee code or name, a loan type, a status. Served by IDX_ELOAN_STATUS / IDX_ELOAN_COMPONENT and the PK.
     */
    @Query(value = """
        SELECT l.LOAN_ID AS loanId, l.EMPLOYEE_ID AS employeeId, e.EMPLOYEE_CODE AS employeeCode,
               TRIM(e.FIRST_NAME || ' ' || e.LAST_NAME) AS employeeName,
               l.COMPONENT_ID AS componentId, c.COMPONENT_CODE AS componentCode,
               c.COMPONENT_NAME AS loanTypeName, c.COMPONENT_NAME_AR AS loanTypeNameAr,
               l.PRINCIPAL AS principal, l.INSTALMENT_COUNT AS instalmentCount, l.MONTHLY_INSTALMENT AS monthlyInstalment,
               TO_CHAR(l.START_MONTH, 'YYYY-MM-DD') AS startMonth, l.INSTALMENTS_PAID AS instalmentsPaid,
               l.STATUS AS status, l.STATUS_REASON AS statusReason, l.REMARKS AS remarks, l.VERSION AS version
        FROM HRMS.EMPLOYEE_LOANS l
        JOIN HRMS.EMPLOYEES e ON e.EMPLOYEE_ID = l.EMPLOYEE_ID
        JOIN HRMS.SALARY_COMPONENTS c ON c.COMPONENT_ID = l.COMPONENT_ID
        WHERE (:status IS NULL OR l.STATUS = :status)
          AND (:componentId IS NULL OR l.COMPONENT_ID = :componentId)
          AND (:employeeId IS NULL OR l.EMPLOYEE_ID = :employeeId)
          AND (:q IS NULL
               OR LOWER(e.EMPLOYEE_CODE) LIKE '%' || LOWER(:q) || '%' ESCAPE '\\'
               OR LOWER(e.FIRST_NAME || ' ' || e.LAST_NAME) LIKE '%' || LOWER(:q) || '%' ESCAPE '\\')
        ORDER BY l.LOAN_ID DESC
        """,
        countQuery = """
        SELECT COUNT(*)
        FROM HRMS.EMPLOYEE_LOANS l
        JOIN HRMS.EMPLOYEES e ON e.EMPLOYEE_ID = l.EMPLOYEE_ID
        WHERE (:status IS NULL OR l.STATUS = :status)
          AND (:componentId IS NULL OR l.COMPONENT_ID = :componentId)
          AND (:employeeId IS NULL OR l.EMPLOYEE_ID = :employeeId)
          AND (:q IS NULL
               OR LOWER(e.EMPLOYEE_CODE) LIKE '%' || LOWER(:q) || '%' ESCAPE '\\'
               OR LOWER(e.FIRST_NAME || ' ' || e.LAST_NAME) LIKE '%' || LOWER(:q) || '%' ESCAPE '\\')
        """,
        nativeQuery = true)
    Page<EmployeeLoanRow> search(@Param("q") String q, @Param("componentId") Long componentId,
                                 @Param("status") String status, @Param("employeeId") Long employeeId, Pageable pageable);

    /** What the employee's running loans take each month: ACTIVE loans that have started by the month, not yet repaid. */
    @Query(value = """
        SELECT COALESCE(SUM(l.MONTHLY_INSTALMENT), 0)
        FROM HRMS.EMPLOYEE_LOANS l
        WHERE l.EMPLOYEE_ID = :employeeId AND l.STATUS = 'ACTIVE'
          AND l.START_MONTH <= :month AND l.INSTALMENTS_PAID < l.INSTALMENT_COUNT
        """, nativeQuery = true)
    BigDecimal monthlyInstalments(@Param("employeeId") Long employeeId, @Param("month") LocalDate month);
}
