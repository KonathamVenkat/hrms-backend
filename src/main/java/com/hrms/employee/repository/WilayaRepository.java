package com.hrms.employee.repository;

import com.hrms.employee.entity.Wilaya;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WilayaRepository extends JpaRepository<Wilaya, Long> {
    List<Wilaya> findAllByOrderByNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    boolean existsByRegionId(Long regionId);
    boolean existsByRegionIdAndIsActive(Long regionId, Boolean isActive);
}
