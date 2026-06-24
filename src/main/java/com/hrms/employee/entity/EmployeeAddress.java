package com.hrms.employee.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * JPA Entity for HRMS.EMPLOYEE_ADDRESSES table.
 *
 * An employee can have multiple addresses of different types.
 * Only one address per type can be PRIMARY (IS_PRIMARY = 1).
 *
 * Address types: PERMANENT | CURRENT | EMERGENCY | MAILING
 */
@Entity
@Table(name = "EMPLOYEE_ADDRESSES", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "emp_addr_seq")
    @SequenceGenerator(
        name           = "emp_addr_seq",
        sequenceName   = "HRMS.SEQ_EMPLOYEE_ADDRESSES",
        allocationSize = 1
    )
    @Column(name = "EMPLOYEE_ADDRESSES_ID", nullable = false)
    private Long employeeAddressesId;

    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    /**
     * PERMANENT | CURRENT | EMERGENCY | MAILING
     * Maps to CHK_ADDR_TYPE constraint
     */
    @Column(name = "ADDRESS_TYPE", nullable = false, length = 20)
    private String addressType;

    @Column(name = "ADDRESS_LINE1", nullable = false, length = 300)
    private String addressLine1;

    @Column(name = "ADDRESS_LINE2", length = 300)
    private String addressLine2;

    @Column(name = "CITY", nullable = false, length = 100)
    private String city;

    @Column(name = "STATE_PROVINCE", length = 100)
    private String stateProvince;

    @Column(name = "COUNTRY", nullable = false, length = 100)
    @Builder.Default
    private String country = "Oman";

    @Column(name = "POSTAL_CODE", length = 20)
    private String postalCode;

    /** 1 = primary address of this type */
    @Column(name = "IS_PRIMARY", nullable = false)
    @Builder.Default
    private Integer isPrimary = 0;

    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Integer isActive = 1;

    @Column(name = "CREATED_AT", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
