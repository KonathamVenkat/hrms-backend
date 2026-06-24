package com.hrms.leave.repository;

import com.hrms.leave.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    List<LeaveType> findByIsActiveOrderBySortOrderAsc(Integer isActive);

    List<LeaveType> findAllByOrderBySortOrderAsc();

    Optional<LeaveType> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameEnIgnoreCase(String nameEn);
    boolean existsByCodeIgnoreCaseAndLeaveTypeIdNot(String code, Long id);
    boolean existsByNameEnIgnoreCaseAndLeaveTypeIdNot(String nameEn, Long id);
}
