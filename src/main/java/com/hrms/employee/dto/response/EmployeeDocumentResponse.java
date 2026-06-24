
package com.hrms.employee.dto.response;
import lombok.*;

@Data @Builder
public class EmployeeDocumentResponse {

    private Long    documentId;
    private Long    employeeId;

    // Document type info
    private Long    docTypeId;
    private String  docTypeCode;
    private String  docTypeName;
    private String  category;
    private Boolean hasExpiry;

    // File info
    private String  documentName;
    private String  originalFileName;
    private Long    fileSize;
    private String  fileExtension;
    private String  fileSizeFormatted;   // e.g. "2.3 MB"
    private String  downloadUrl;         // served by controller

    // Document details
    private String  documentNumber;
    private String  issueDate;
    private String  expiryDate;
    private String  issuedBy;
    private String  notes;

    // Status
    private Boolean isVerified;
    private String  verifiedBy;
    private String  verifiedAt;
    private Boolean isExpired;           // computed
    private Boolean isExpiringSoon;      // computed — within 30 days

    // Audit
    private String  uploadedBy;
    private String  createdAt;
}
