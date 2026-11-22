package com.hrms.employee;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V31 against a real Oracle schema: rates keep 10 decimals (Armed Forces 1/30 of gross = 3.3333333333), and the way
 * V31 widens the columns works on a column that already holds values, as on a live database.
 */
class PercentageTenDecimalsDbTest extends DatabaseTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    void aStructureItemAndAComponentKeepTenDecimals() {
        jdbc.update("insert into HRMS.SALARY_COMPONENTS (COMPONENT_ID, COMPONENT_CODE, COMPONENT_NAME, COMPONENT_NAME_AR, "
            + "COMPONENT_TYPE, CALC_TYPE, DEFAULT_VALUE, VERSION) values (993101, 'V31_AF', 'V31 armed forces', N'x', "
            + "'STATUTORY', 'PERCENTAGE_OF_GROSS', 3.3333333333, 0)");
        jdbc.update("insert into HRMS.SALARY_STRUCTURES (STRUCTURE_ID, STRUCTURE_CODE, STRUCTURE_NAME, VERSION) "
            + "values (993101, 'V31_S', 'V31 structure', 0)");
        jdbc.update("insert into HRMS.SALARY_STRUCTURE_ITEMS (ITEM_ID, STRUCTURE_ID, COMPONENT_ID, PERCENTAGE) "
            + "values (993101, 993101, 993101, 3.3333333333)");

        assertEquals(0, new BigDecimal("3.3333333333").compareTo(jdbc.queryForObject(
            "select PERCENTAGE from HRMS.SALARY_STRUCTURE_ITEMS where ITEM_ID = 993101", BigDecimal.class)));
        assertEquals(0, new BigDecimal("3.3333333333").compareTo(jdbc.queryForObject(
            "select DEFAULT_VALUE from HRMS.SALARY_COMPONENTS where COMPONENT_ID = 993101", BigDecimal.class)));
    }

    /** Not transactional (DDL commits): one connection, like a script session, and a scratch table dropped at the end. */
    @Test
    void widenDigitsAfterThePointOnAColumnThatHoldsValues() {
        jdbc.execute((ConnectionCallback<Void>) con -> {
            try (Statement st = con.createStatement()) {
                st.execute("create table HRMS.V31_WIDEN_TEST (P NUMBER(6,2), D NUMBER(12,2))");
                try {
                    st.executeUpdate("insert into HRMS.V31_WIDEN_TEST (P, D) values (3.33, 1598050.80)");
                    con.commit();
                    st.execute("alter table HRMS.V31_WIDEN_TEST modify (P NUMBER(14,10))");
                    st.execute("alter table HRMS.V31_WIDEN_TEST modify (D NUMBER(20,10))");
                    assertEquals(0, new BigDecimal("3.33").compareTo(one(st, "select P from HRMS.V31_WIDEN_TEST")));
                    assertEquals(0, new BigDecimal("1598050.80").compareTo(one(st, "select D from HRMS.V31_WIDEN_TEST")));
                    st.executeUpdate("update HRMS.V31_WIDEN_TEST set P = 3.3333333333");
                    assertEquals(0, new BigDecimal("3.3333333333").compareTo(one(st, "select P from HRMS.V31_WIDEN_TEST")));
                } finally {
                    st.execute("drop table HRMS.V31_WIDEN_TEST purge");
                }
            }
            return null;
        });
    }

    private static BigDecimal one(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }
}
