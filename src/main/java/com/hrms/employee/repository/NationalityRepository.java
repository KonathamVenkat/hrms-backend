package com.hrms.employee.repository;

import com.hrms.employee.entity.Nationality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NationalityRepository extends JpaRepository<Nationality, Long> {
    List<Nationality> findAllByOrderByNameAsc();
    List<Nationality> findByIsActiveOrderByNameAsc(Boolean isActive);
    List<Nationality> findByIsActiveAndCountryNameIsNotNullOrderByCountryNameAsc(Boolean isActive);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
