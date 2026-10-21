package com.hrms.employee.repository;

import com.hrms.employee.entity.Relation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RelationRepository extends JpaRepository<Relation, Long> {
    List<Relation> findAllByOrderByNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    List<Relation> findByIsActiveOrderByNameAsc(Boolean isActive);
    boolean existsByRelationTypeId(Long relationTypeId);
    boolean existsByRelationTypeIdAndIsActive(Long relationTypeId, Boolean isActive);
}
