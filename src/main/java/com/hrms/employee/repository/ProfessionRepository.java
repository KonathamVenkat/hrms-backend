package com.hrms.employee.repository;

import com.hrms.employee.entity.Profession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProfessionRepository extends JpaRepository<Profession, Long> {
    List<Profession> findAllByOrderByNameAsc();
    List<Profession> findByIsActiveOrderByNameAsc(Boolean isActive);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
