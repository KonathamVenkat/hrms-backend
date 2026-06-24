package com.hrms.employee.service;



import com.hrms.employee.dto.request.EmployeeDocumentRequest;
import com.hrms.employee.dto.response.EmployeeDocumentResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface EmployeeDocumentService {

    List<EmployeeDocumentResponse> getDocuments(Long employeeId);
    EmployeeDocumentResponse       getDocumentById(Long employeeId, Long documentId);
    EmployeeDocumentResponse       uploadDocument(Long employeeId,
                                                  EmployeeDocumentRequest request,
                                                  MultipartFile file);
    EmployeeDocumentResponse       updateDocumentInfo(Long employeeId,
                                                      Long documentId,
                                                      EmployeeDocumentRequest request);
    EmployeeDocumentResponse       verifyDocument(Long employeeId,
                                                  Long documentId,
                                                  String verifiedBy);
    void                           deleteDocument(Long employeeId, Long documentId);
    Resource                       downloadDocument(Long employeeId, Long documentId);
}