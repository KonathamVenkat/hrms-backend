package com.hrms.recruitment.repository;

import com.hrms.recruitment.entity.RequisitionApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequisitionApprovalRepository extends JpaRepository<RequisitionApproval, Long> {

    List<RequisitionApproval> findByRequisitionIdOrderByLevelNoAsc(Long requisitionId);
}
