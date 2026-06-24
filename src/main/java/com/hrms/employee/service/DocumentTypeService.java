package com.hrms.employee.service;

import com.hrms.employee.dto.request.DocumentTypeRequest;
import com.hrms.employee.dto.response.DocumentTypeResponse;
import java.util.List;

public interface DocumentTypeService {
    List<DocumentTypeResponse> getAllDocumentTypes();
    List<DocumentTypeResponse> getActiveDocumentTypes();
    List<DocumentTypeResponse> getMandatoryDocumentTypes();
    DocumentTypeResponse       getDocumentTypeById(Long id);
    DocumentTypeResponse       createDocumentType(DocumentTypeRequest request);
    DocumentTypeResponse       updateDocumentType(Long id, DocumentTypeRequest request);
    void                       deactivateDocumentType(Long id);
    void                       activateDocumentType(Long id);
}
