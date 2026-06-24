package com.hrms.employee.repository;

import com.hrms.employee.entity.OfficeLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OfficeLocationRepository extends JpaRepository<OfficeLocation, Long> {

    List<OfficeLocation> findAllByOrderBySortOrderAsc();
    List<OfficeLocation> findByIsActiveOrderBySortOrderAsc(Integer isActive);
    List<OfficeLocation> findByLocationTypeAndIsActiveOrderBySortOrderAsc(
            String locationType, Integer isActive);

    boolean existsByLocationCodeIgnoreCase(String locationCode);
    boolean existsByLocationNameIgnoreCase(String locationName);
    boolean existsByLocationCodeIgnoreCaseAndLocationIdNot(String code, Long id);
    boolean existsByLocationNameIgnoreCaseAndLocationIdNot(String name, Long id);
}
