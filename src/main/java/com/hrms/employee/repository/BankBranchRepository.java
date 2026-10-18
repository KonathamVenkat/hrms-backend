package com.hrms.employee.repository;

import com.hrms.employee.entity.BankBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BankBranchRepository extends JpaRepository<BankBranch, Long> {
    List<BankBranch> findAllByOrderByNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    List<BankBranch> findByBankIdAndIsActiveOrderByNameAsc(Long bankId, Boolean isActive);
    boolean existsByBankId(Long bankId);
    boolean existsByBankIdAndIsActive(Long bankId, Boolean isActive);
}
