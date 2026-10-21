package com.hrms.employee.repository;

import com.hrms.employee.entity.Directorate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DirectorateRepository extends JpaRepository<Directorate, Long> {
    List<Directorate> findAllByOrderByNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    boolean existsByCompanyBranchId(Long companyBranchId);
    boolean existsByCompanyBranchIdAndIsActive(Long companyBranchId, Boolean isActive);
}
