package com.hrms.workflow.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.workflow.entity.WorkflowDefinition;
import com.hrms.workflow.enums.RequestType;
import com.hrms.workflow.repository.WorkflowDefinitionRepository;
import com.hrms.workflow.service.RequestCompletionHandler;
import com.hrms.workflow.service.WorkflowRequestAccess;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The request modules' ports and completion handlers by request type. A type without them is fine until it has an
 * ACTIVE workflow; then startup fails, so a published flow can never run without its downstream effects.
 */
@Component
class WorkflowPorts {

    private final Map<RequestType, WorkflowRequestAccess> access;
    private final Map<RequestType, RequestCompletionHandler> handlers;
    private final WorkflowDefinitionRepository definitions;

    WorkflowPorts(List<WorkflowRequestAccess> access, List<RequestCompletionHandler> handlers,
                  WorkflowDefinitionRepository definitions) {
        this.access = byType(access, WorkflowRequestAccess::type);
        this.handlers = byType(handlers, RequestCompletionHandler::type);
        this.definitions = definitions;
    }

    private static <T> Map<RequestType, T> byType(List<T> beans, Function<T, RequestType> type) {
        Map<RequestType, T> map = new EnumMap<>(RequestType.class);
        for (T bean : beans) {
            T previous = map.put(type.apply(bean), bean);
            if (previous != null) {
                throw new IllegalStateException("Two workflow beans for request type " + type.apply(bean) + ": "
                    + previous.getClass().getName() + " and " + bean.getClass().getName());
            }
        }
        return map;
    }

    @PostConstruct
    void verify() {
        for (RequestType type : RequestType.values()) {
            boolean wired = access.containsKey(type) && handlers.containsKey(type);
            if (!wired && definitions.findByRequestTypeAndStatus(type, WorkflowDefinition.STATUS_ACTIVE).isPresent()) {
                throw new IllegalStateException("Request type " + type + " has an ACTIVE workflow but no "
                    + "WorkflowRequestAccess / RequestCompletionHandler bean");
            }
        }
    }

    java.util.Collection<WorkflowRequestAccess> allAccess() {
        return access.values();
    }

    WorkflowRequestAccess access(RequestType type) {
        return require(access.get(type), type);
    }

    RequestCompletionHandler handler(RequestType type) {
        return require(handlers.get(type), type);
    }

    private static <T> T require(T bean, RequestType type) {
        if (bean == null) {
            throw new BusinessRuleException("WORKFLOW_NOT_SUPPORTED", "Request type " + type + " has no workflow.");
        }
        return bean;
    }
}
