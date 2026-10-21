package com.hrms.employee.repository;

import com.hrms.employee.entity.CompanyBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CompanyBranchRepository extends JpaRepository<CompanyBranch, Long> {
    List<CompanyBranch> findAllByOrderByNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    boolean existsByCompanyId(Long companyId);
    boolean existsByCompanyIdAndIsActive(Long companyId, Boolean isActive);

    List<CompanyBranch> findByIsActiveTrueOrderByNameAsc();

    List<CompanyBranch> findByLocationIdAndIsActiveTrueOrderByNameAsc(Long locationId);

    List<CompanyBranch> findByBranchManagerEmployeeId(Long employeeId);

    long countByIsActiveTrueAndBranchManagerEmployeeIdIsNull();

    long countByIsActiveTrueAndLocationIdIsNull();
}
