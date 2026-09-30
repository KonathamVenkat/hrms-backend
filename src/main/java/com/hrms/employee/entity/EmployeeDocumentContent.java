package com.hrms.employee.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * File bytes of an {@link EmployeeDocument}, in HRMS.EMPLOYEE_DOCUMENT_CONTENT.
 *
 * Kept in its own table (primary key = the document's id) so that listing or editing a
 * document's metadata never reads the BLOB.
 */
@Entity
@Table(name = "EMPLOYEE_DOCUMENT_CONTENT", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EmployeeDocumentContent {

    @Id
    @Column(name = "DOCUMENT_ID", nullable = false)
    private Long documentId;

    @Lob
    @Column(name = "CONTENT", nullable = false)
    private byte[] content;
}
