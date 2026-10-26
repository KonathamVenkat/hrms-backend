package com.hrms.workflow.repository;

import com.hrms.workflow.entity.WorkflowSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowSettingRepository extends JpaRepository<WorkflowSetting, String> {
}
