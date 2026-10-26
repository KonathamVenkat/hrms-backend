package com.hrms.workflow.entity;

import com.hrms.common.audit.Auditable;
import com.hrms.workflow.enums.StepOutcome;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Where a step's outcome leads. A condition is field + operator + value, all three or none (none = unconditional,
 * the "otherwise" route). TARGET is a step key, APPROVED or REJECTED.
 */
@Entity
@Table(name = "WORKFLOW_ROUTES", schema = "HRMS")
@Getter @Setter @NoArgsConstructor
public class WorkflowRoute extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "wf_routes_seq")
    @SequenceGenerator(name = "wf_routes_seq", sequenceName = "HRMS.SEQ_WORKFLOW_ROUTES", allocationSize = 1)
    @Column(name = "ROUTE_ID", nullable = false)
    private Long routeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "STEP_ID", nullable = false)
    private WorkflowStep step;

    @Enumerated(EnumType.STRING)
    @Column(name = "OUTCOME", nullable = false, length = 10)
    private StepOutcome outcome;

    @Column(name = "COND_FIELD", length = 30)
    private String condField;

    @Column(name = "COND_OPERATOR", length = 2)
    private String condOperator;

    /**
     * Held as text so the validator can reject a non-numeric value from the admin API before it ever reaches the
     * NUMBER(14,3) column (the converter maps it to BigDecimal, so Hibernate validates the column as a number).
     */
    @Convert(converter = NumericText.class)
    @Column(name = "COND_VALUE")
    private String condValue;

    @Column(name = "TARGET", nullable = false, length = 30)
    private String target;

    @Column(name = "POSITION_NO", nullable = false)
    private Integer positionNo = 1;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;

    @Converter
    public static class NumericText implements AttributeConverter<String, BigDecimal> {
        @Override
        public BigDecimal convertToDatabaseColumn(String text) {
            return text == null ? null : new BigDecimal(text.trim());
        }

        @Override
        public String convertToEntityAttribute(BigDecimal number) {
            return number == null ? null : number.toPlainString();
        }
    }
}
