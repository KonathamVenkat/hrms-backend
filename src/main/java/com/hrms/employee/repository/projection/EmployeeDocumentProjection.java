package com.hrms.employee.repository.projection;

public interface EmployeeDocumentProjection {

    Long    getDocumentId();
    Long    getEmployeeId();
    Long    getDocTypeId();
    String  getDocTypeCode();
    String  getDocTypeName();
    String  getCategory();
    Integer getHasExpiry();

    String  getDocumentName();
    String  getOriginalFileName();
    Long    getFileSize();
    String  getFileExtension();

    String  getDocumentNumber();
    String  getIssueDate();
    String  getExpiryDate();
    String  getIssuedBy();
    String  getNotes();

    Integer getIsVerified();
    String  getVerifiedBy();
    String  getVerifiedAt();

    Integer getIsActive();
    String  getUploadedBy();
    String  getCreatedAt();
}
