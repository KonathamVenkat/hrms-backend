package com.hrms.workflow.repository;

import com.hrms.workflow.entity.WorkflowRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRouteRepository extends JpaRepository<WorkflowRoute, Long> {

    List<WorkflowRoute> findByStepDefinitionId(Long definitionId);
}
