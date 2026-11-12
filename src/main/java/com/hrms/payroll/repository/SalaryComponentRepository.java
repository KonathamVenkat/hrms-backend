package com.hrms.payroll.repository;

import com.hrms.payroll.entity.SalaryComponent;
import com.hrms.payroll.enums.ComponentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    /** Row lock for one component: its edit or deactivation waits for salary assignments and structure edits using it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM SalaryComponent c WHERE c.componentId = :id")
    Optional<SalaryComponent> findByIdForUpdate(@Param("id") Long id);

    /** Row locks taken in id order, so two callers locking overlapping sets cannot deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM SalaryComponent c WHERE c.componentId IN :ids ORDER BY c.componentId")
    List<SalaryComponent> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    boolean existsByComponentCode(String code);
    boolean existsByComponentName(String name);

    /** Employees whose current salary uses a structure that contains this component. */
    @Query(value = """
            SELECT COUNT(DISTINCT es.EMPLOYEE_ID) FROM HRMS.EMPLOYEE_SALARY es
            JOIN HRMS.SALARY_STRUCTURE_ITEMS i ON i.STRUCTURE_ID = es.STRUCTURE_ID
            WHERE i.COMPONENT_ID = :componentId AND es.IS_CURRENT = 1
            """, nativeQuery = true)
    int countCurrentEmployeesByComponentId(Long componentId);
}
