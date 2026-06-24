package com.hrms.payroll.repository;

import com.hrms.payroll.entity.SalaryStructureItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalaryStructureItemRepository
        extends JpaRepository<SalaryStructureItem, Long> {

    @Query(value = "SELECT HRMS.SEQ_SALARY_STR_ITEMS.NEXTVAL FROM DUAL",
            nativeQuery = true)
    Long findNextSequenceValue();

    List<SalaryStructureItem> findByStructureIdAndIsActiveOrderBySortOrderAsc(
            Long structureId, Integer isActive);

    void deleteByStructureId(Long structureId);

    boolean existsByStructureIdAndComponentId(Long structureId, Long componentId);
}
