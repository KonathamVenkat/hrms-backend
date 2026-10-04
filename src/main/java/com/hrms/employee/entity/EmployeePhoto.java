package com.hrms.employee.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Uploaded profile photo of an employee, in HRMS.EMPLOYEE_PHOTO (primary key = the employee's id).
 *
 * Kept in its own table so that listing or editing an employee never reads the BLOB.
 */
@Entity
@Table(name = "EMPLOYEE_PHOTO", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EmployeePhoto {

    @Id
    @Column(name = "EMPLOYEE_ID", nullable = false)
    private Long employeeId;

    @Lob
    @Column(name = "CONTENT", nullable = false)
    private byte[] content;

    @Column(name = "CONTENT_TYPE", nullable = false, length = 50)
    private String contentType;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;
}
