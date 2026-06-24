package com.hrms.payroll.repository;

import com.hrms.payroll.entity.SalaryComponent;
import com.hrms.payroll.enums.ComponentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalaryComponentRepository
        extends JpaRepository<SalaryComponent, Long> {

    @Query(value = "SELECT HRMS.SEQ_SALARY_COMPONENTS.NEXTVAL FROM DUAL",
            nativeQuery = true)
    Long findNextSequenceValue();

    List<SalaryComponent> findByIsActiveOrderBySortOrderAsc(Integer isActive);

    List<SalaryComponent> findByComponentTypeAndIsActiveOrderBySortOrderAsc(
            ComponentType type, Integer isActive);

    Optional<SalaryComponent> findByComponentCode(String code);

    boolean existsByComponentCode(String code);
    boolean existsByComponentName(String name);
}
