package com.hrms.recruitment.repository;

import com.hrms.recruitment.entity.RejectionReason;
import com.hrms.recruitment.enums.ReasonScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RejectionReasonRepository extends JpaRepository<RejectionReason, Long> {

    List<RejectionReason> findAllByOrderBySortOrderAscNameAsc();

    List<RejectionReason> findByIsActiveAndAppliesToOrderBySortOrderAscNameAsc(Boolean isActive, ReasonScope appliesTo);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
