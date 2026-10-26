package com.hrms.workflow.repository;

import com.hrms.workflow.entity.WorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, Long> {

    List<WorkflowStep> findByDefinitionIdOrderByPositionNo(Long definitionId);

    /** Keys of person (non-ROLE) steps in any version: the stages whose stored approver follows the organisation. */
    @Query("""
        SELECT DISTINCT s.stepKey FROM WorkflowStep s
        WHERE s.approverType <> com.hrms.workflow.enums.ApproverType.ROLE
        """)
    List<String> findPersonStepKeys();
}
