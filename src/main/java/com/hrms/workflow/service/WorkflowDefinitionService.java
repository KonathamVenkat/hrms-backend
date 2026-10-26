package com.hrms.workflow.service;

import com.hrms.workflow.entity.WorkflowDefinition;
import com.hrms.workflow.enums.RequestType;

import java.util.Optional;

public interface WorkflowDefinitionService {

    /** Builds the default flow in memory, validates, deploys it and makes it the one ACTIVE version (old one RETIRED). */
    WorkflowDefinition publishDefault(RequestType type);

    Optional<WorkflowDefinition> active(RequestType type);
}
