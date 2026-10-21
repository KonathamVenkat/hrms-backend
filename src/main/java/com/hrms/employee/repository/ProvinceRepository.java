package com.hrms.employee.repository;

import com.hrms.employee.entity.Province;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProvinceRepository extends JpaRepository<Province, Long> {
    List<Province> findAllByOrderByNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    boolean existsByWilayaId(Long wilayaId);
    boolean existsByWilayaIdAndIsActive(Long wilayaId, Boolean isActive);
}
