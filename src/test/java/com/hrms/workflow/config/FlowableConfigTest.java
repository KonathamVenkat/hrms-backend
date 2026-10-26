package com.hrms.workflow.config;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The engine settings the workflow depends on are forced in code, whatever the properties say. */
class FlowableConfigTest {

    private SpringProcessEngineConfiguration configured(HistoryLevel fromProperties) {
        SpringProcessEngineConfiguration config = new SpringProcessEngineConfiguration();
        config.setHistoryLevel(fromProperties);
        config.setDisableIdmEngine(false);
        config.setDisableEventRegistry(false);
        new FlowableConfig().flowableSchemaPrefix().configure(config);
        return config;
    }

    /** FlowEngine.terminalStage reads the end event from history, which needs at least ACTIVITY. */
    @Test
    void historyIsRaisedToActivityButNeverLowered() {
        assertEquals(HistoryLevel.ACTIVITY, configured(HistoryLevel.NONE).getHistoryLevel());
        assertEquals(HistoryLevel.ACTIVITY, configured(HistoryLevel.ACTIVITY).getHistoryLevel());
        assertEquals(HistoryLevel.FULL, configured(HistoryLevel.FULL).getHistoryLevel());
    }

    @Test
    void unsetHistoryLevelIsAuditNotLowered() {
        assertEquals(HistoryLevel.AUDIT, configured(null).getHistoryLevel());
    }

    @Test
    void idmAndEventRegistryAreOff() {
        SpringProcessEngineConfiguration config = configured(HistoryLevel.AUDIT);
        assertTrue(config.isDisableIdmEngine());
        assertTrue(config.isDisableEventRegistry());
    }
}
