package com.hrms.employee.repository;

import com.hrms.employee.entity.EmployeeBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeBankAccountRepository extends JpaRepository<EmployeeBankAccount, Long> {

    List<EmployeeBankAccount> findByEmployeeIdOrderByIdAsc(Long employeeId);

    /** Deactivated rows count: the table's unique key (bank, account number) still holds them. */
    boolean existsByBankIdAndAccountNumber(Long bankId, String accountNumber);

    boolean existsByBankIdAndAccountNumberAndIdNot(Long bankId, String accountNumber, Long id);

    long countByEmployeeIdAndIsActive(Long employeeId, Boolean isActive);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE EmployeeBankAccount a SET a.isPrimary = false WHERE a.employeeId = :employeeId AND a.isPrimary = true")
    int unsetAllPrimary(@Param("employeeId") Long employeeId);
}
