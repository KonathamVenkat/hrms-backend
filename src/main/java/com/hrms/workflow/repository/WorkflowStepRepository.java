package com.hrms.workflow.repository;

import com.hrms.workflow.entity.WorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, Long> {

    List<WorkflowStep> findByDefinitionIdOrderByPositionNo(Long definitionId);
}
