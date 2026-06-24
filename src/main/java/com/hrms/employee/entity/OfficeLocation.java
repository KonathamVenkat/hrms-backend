package com.hrms.employee.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name   = "OFFICE_LOCATIONS",
    schema = "HRMS",
    uniqueConstraints = {
        @UniqueConstraint(name = "UQ_LOCATION_CODE", columnNames = "LOCATION_CODE"),
        @UniqueConstraint(name = "UQ_LOCATION_NAME", columnNames = "LOCATION_NAME")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OfficeLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "office_loc_seq")
    @SequenceGenerator(
        name = "office_loc_seq",
        sequenceName = "HRMS.SEQ_OFFICE_LOCATIONS",
        allocationSize = 1
    )
    @Column(name = "LOCATION_ID", nullable = false)
    private Long locationId;

    @Column(name = "LOCATION_CODE", nullable = false, length = 20)
    private String locationCode;

    @Column(name = "LOCATION_NAME", nullable = false, length = 200)
    private String locationName;

    @Column(name = "LOCATION_NAME_AR", nullable = false,
            columnDefinition = "NVARCHAR2(200)")
    private String locationNameAr;

    /** HEAD_OFFICE | BRANCH | REMOTE | WAREHOUSE | SITE */
    @Column(name = "LOCATION_TYPE", nullable = false, length = 30)
    @Builder.Default
    private String locationType = "BRANCH";

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

    @Column(name = "PHONE", length = 30)
    private String phone;

    @Column(name = "EMAIL", length = 200)
    private String email;

    @Column(name = "TIMEZONE", length = 50)
    @Builder.Default
    private String timezone = "Asia/Muscat";

    @Column(name = "LATITUDE", precision = 10, scale = 6)
    private BigDecimal latitude;

    @Column(name = "LONGITUDE", precision = 10, scale = 6)
    private BigDecimal longitude;

    @Column(name = "IS_ACTIVE", nullable = false)
    @Builder.Default
    private Integer isActive = 1;

    @Column(name = "SORT_ORDER", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "CREATED_BY", length = 50)
    private String createdBy;

    @Column(name = "CREATED_AT", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UPDATED_BY", length = 50)
    private String updatedBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
