package com.hrms.employee.service.impl;

import com.hrms.employee.dto.response.EmployeeCompletenessResponse;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.EmployeeChecks;
import com.hrms.employee.service.EmployeeCompletenessService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One round trip for all eleven checks: a single SELECT of EXISTS sub-queries (nothing is loaded). A tab counts as
 * filled when it has an active record: a current job, an identity record, and an active (not deactivated or
 * removed) address, bank account, document, dependant, education, training, language, experience or reference.
 * Every table is read by EMPLOYEE_ID, which is indexed on each of them.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeCompletenessServiceImpl implements EmployeeCompletenessService {

    private static final String ACTIVE = "IS_ACTIVE = 1";

    /** The order here is the order of the columns read back in {@link #getCompleteness}. */
    private static final String SQL = "SELECT "
        + exists("EMPLOYEE_JOB_DETAILS", "IS_CURRENT = 1") + ", "
        + exists("EMPLOYEE_ADDRESSES", ACTIVE) + ", "
        + exists("EMPLOYEE_IDENTITY_INFO", "1 = 1") + ", "
        + exists("EMPLOYEE_BANK_ACCOUNTS", ACTIVE) + ", "
        + exists("EMPLOYEE_DOCUMENTS", ACTIVE) + ", "
        + exists("EMPLOYEE_DEPENDENTS", ACTIVE) + ", "
        + exists("EMPLOYEE_EDUCATION", ACTIVE) + ", "
        + exists("EMPLOYEE_TRAINING", ACTIVE) + ", "
        + exists("EMPLOYEE_LANGUAGES", ACTIVE) + ", "
        + exists("EMPLOYEE_EXPERIENCE", ACTIVE) + ", "
        + exists("EMPLOYEE_REFERENCES", ACTIVE)
        + " FROM DUAL";

    private final EmployeeRepository employeeRepository;
    private final EntityManager entityManager;

    /** Table and condition are constants of this class; the employee id is always a bind parameter. */
    private static String exists(String table, String condition) {
        return "CASE WHEN EXISTS (SELECT 1 FROM HRMS." + table + " WHERE EMPLOYEE_ID = :id AND " + condition
            + ") THEN 1 ELSE 0 END";
    }

    @Override
    public EmployeeCompletenessResponse getCompleteness(Long employeeId) {
        EmployeeChecks.requireExists(employeeRepository, employeeId);
        Object[] row = (Object[]) entityManager.createNativeQuery(SQL)
            .setParameter("id", employeeId)
            .getSingleResult();
        boolean[] has = new boolean[row.length];
        for (int i = 0; i < row.length; i++) {
            has[i] = ((Number) row[i]).intValue() == 1;
        }
        return new EmployeeCompletenessResponse(
            has[0], has[1], has[2], has[3], has[4], has[5],
            has[6] && has[7] && has[8],
            has[9] && has[10]);
    }
}
