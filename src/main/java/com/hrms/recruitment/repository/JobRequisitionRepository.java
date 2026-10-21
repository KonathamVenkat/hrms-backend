package com.hrms.recruitment.repository;

import com.hrms.recruitment.entity.JobRequisition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface JobRequisitionRepository
        extends JpaRepository<JobRequisition, Long>, JpaSpecificationExecutor<JobRequisition> {

    /** Next number of the year-prefixed code REQ-YYYY-NNNNNN (the year part is added in Java). */
    @Query(value = "SELECT HRMS.SEQ_REC_REQ_CODE.NEXTVAL FROM DUAL", nativeQuery = true)
    Long nextCodeNumber();
}
