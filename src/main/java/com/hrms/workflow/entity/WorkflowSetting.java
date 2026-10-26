package com.hrms.workflow.entity;

import com.hrms.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Global switch: key and the text value ("true" / "false"). The key is the primary key (no sequence). */
@Entity
@Table(name = "WORKFLOW_SETTINGS", schema = "HRMS")
@Getter @Setter @NoArgsConstructor
public class WorkflowSetting extends Auditable {

    @Id
    @Column(name = "SETTING_KEY", nullable = false, length = 60)
    private String settingKey;

    @Column(name = "SETTING_VALUE", nullable = false, length = 200)
    private String settingValue;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}
