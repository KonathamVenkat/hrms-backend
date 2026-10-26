package com.hrms.workflow.service.impl;

import com.hrms.workflow.repository.WorkflowSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Whether SYSTEM_ADMIN may manage workflows too (off by default; HR_ADMIN always may). Used in @PreAuthorize. */
@Component("workflowAdminPolicy")
@RequiredArgsConstructor
public class WorkflowAdminPolicy {

    static final String SYSTEM_ADMIN_SETTING = "SYSTEM_ADMIN_CAN_MANAGE_WORKFLOWS";

    private final WorkflowSettingRepository settings;

    @Transactional(readOnly = true)
    public boolean systemAdminMayManage() {
        return settings.findById(SYSTEM_ADMIN_SETTING)
            .map(s -> s.getSettingValue() != null && "true".equalsIgnoreCase(s.getSettingValue().trim()))
            .orElse(false);
    }
}
