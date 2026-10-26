package com.hrms.workflow;

import com.hrms.employee.DatabaseTest;
import com.hrms.employee.EmployeeApplication;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


import static org.junit.jupiter.api.Assertions.*;

/** The embedded Flowable engine runs in schema FLOWABLE on the shared datasource without disturbing HRMS queries. */
@SpringBootTest(classes = EmployeeApplication.class)
class FlowableConfigDbTest extends DatabaseTest {

    private static final String BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     targetNamespace="http://hrms.test/bpmn">
          <process id="trivial" isExecutable="true">
            <startEvent id="start"/>
            <sequenceFlow id="f1" sourceRef="start" targetRef="review"/>
            <userTask id="review"/>
            <sequenceFlow id="f2" sourceRef="review" targetRef="end"/>
            <endEvent id="end"/>
          </process>
        </definitions>
        """;

    @Autowired RepositoryService repositoryService;
    @Autowired RuntimeService runtimeService;
    @Autowired TaskService taskService;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext context;
    @Autowired DataSource dataSource;
    @Autowired ProcessEngineConfiguration engineConfiguration;

    @Test
    void deploysStartsAndCompletesATrivialProcessThenHrmsTablesStillResolve() {
        tx.executeWithoutResult(s -> repositoryService.createDeployment()
            .addString("trivial.bpmn20.xml", BPMN).deploy());

        String instanceId = tx.execute(s -> runtimeService.startProcessInstanceByKey("trivial").getId());
        Task task = taskService.createTaskQuery().processInstanceId(instanceId).singleResult();
        assertEquals("review", task.getTaskDefinitionKey());

        tx.executeWithoutResult(s -> taskService.complete(task.getId()));
        assertNull(runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).singleResult());

        // The Flowable rows live in FLOWABLE, and plain HRMS names still resolve on the pooled connections.
        assertTrue(jdbc.queryForObject("select count(*) from FLOWABLE.ACT_RE_PROCDEF", Long.class) > 0);
        assertNotNull(jdbc.queryForObject("select count(*) from EMPLOYEES", Long.class));
        assertEquals(List.of(), nonHrmsSchemas(10));
    }

    /** Holds several connections open at once so the pool has to hand out fresh ones, and reports any not on HRMS. */
    private List<String> nonHrmsSchemas(int connections) {
        List<Connection> held = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        try {
            for (int i = 0; i < connections; i++) {
                Connection c = dataSource.getConnection();
                held.add(c);
                try (var st = c.createStatement();
                     ResultSet rs = st.executeQuery("select sys_context('USERENV','CURRENT_SCHEMA') from dual")) {
                    rs.next();
                    if (!"HRMS".equals(rs.getString(1))) {
                        wrong.add(rs.getString(1));
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        } finally {
            for (Connection c : held) {
                try {
                    c.close();
                } catch (SQLException ignored) {
                    // closing a test connection
                }
            }
        }
        return wrong;
    }

    @Test
    void safetySettingsComeFromCodeNotFromProperties() {
        assertEquals("false", engineConfiguration.getDatabaseSchemaUpdate());
        assertFalse(engineConfiguration.isAsyncExecutorActivate());
        assertEquals("FLOWABLE.", engineConfiguration.getDatabaseTablePrefix());
        assertTrue(engineConfiguration.isTablePrefixIsSchema());
    }

    @Test
    void noFlowableRestOrIdmBeans() {
        assertEquals(1, context.getBeansOfType(ProcessEngine.class).size());
        for (String name : context.getBeanDefinitionNames()) {
            assertFalse(name.toLowerCase().contains("flowablerest"), name);
            assertFalse(name.toLowerCase().contains("idmengine"), name);
        }
    }
}
