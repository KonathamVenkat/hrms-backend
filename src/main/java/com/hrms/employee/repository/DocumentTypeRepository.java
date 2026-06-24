package com.hrms.employee.repository;

import com.hrms.employee.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {

    List<DocumentType> findAllByOrderBySortOrderAsc();
    List<DocumentType> findByIsActiveOrderBySortOrderAsc(Integer isActive);
    List<DocumentType> findByIsMandatoryAndIsActiveOrderBySortOrderAsc(
            Integer isMandatory, Integer isActive);
    List<DocumentType> findByCategoryAndIsActiveOrderBySortOrderAsc(
            String category, Integer isActive);

    boolean existsByDocTypeCodeIgnoreCase(String docTypeCode);
    boolean existsByDocTypeNameIgnoreCase(String docTypeName);
    boolean existsByDocTypeCodeIgnoreCaseAndDocTypeIdNot(String code, Long id);
    boolean existsByDocTypeNameIgnoreCaseAndDocTypeIdNot(String name, Long id);
}
