package com.hrms.workflow.repository;

import com.hrms.workflow.entity.WorkflowDefinition;
import com.hrms.workflow.enums.RequestType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, Long> {

    Optional<WorkflowDefinition> findByRequestTypeAndStatus(RequestType requestType, String status);

    Optional<WorkflowDefinition> findTopByRequestTypeOrderByVersionNoDesc(RequestType requestType);

    /** The version a running request is on, from its Flowable task's process definition. */
    Optional<WorkflowDefinition> findByFlowableProcessDefinitionId(String flowableProcessDefinitionId);
}
