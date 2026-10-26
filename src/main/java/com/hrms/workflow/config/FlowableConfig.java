package com.hrms.workflow.config;

import org.flowable.common.engine.impl.AbstractEngineConfiguration;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Points the embedded Flowable engine at the FLOWABLE schema by qualifying table names ("FLOWABLE.ACT_...").
 * flowable.database-schema is deliberately not used: it runs ALTER SESSION SET CURRENT_SCHEMA on a pooled
 * connection, which then leaks into every other query. Tables are created by db/flowable_schema_setup.sql.
 */
@Configuration(proxyBeanMethods = false)
public class FlowableConfig {

    @Bean
    EngineConfigurationConfigurer<SpringProcessEngineConfiguration> flowableSchemaPrefix() {
        return config -> {
            config.setDatabaseTablePrefix("FLOWABLE.");
            config.setTablePrefixIsSchema(true);
            // Safety settings in code so they never depend on the git-ignored application.properties.
            config.setDatabaseSchemaUpdate(AbstractEngineConfiguration.DB_SCHEMA_UPDATE_FALSE);
            config.setAsyncExecutorActivate(false);
            config.setDisableIdmEngine(true);
            config.setDisableEventRegistry(true);
            // FlowEngine.terminalStage reads the end event the instance reached from history: needs ACTIVITY or more.
            // An unset level is Flowable's default (AUDIT): keep it, never lower it.
            if (config.getHistoryLevel() == null) {
                config.setHistoryLevel(HistoryLevel.AUDIT);
            } else if (!config.getHistoryLevel().isAtLeast(HistoryLevel.ACTIVITY)) {
                config.setHistoryLevel(HistoryLevel.ACTIVITY);
            }
        };
    }
}
