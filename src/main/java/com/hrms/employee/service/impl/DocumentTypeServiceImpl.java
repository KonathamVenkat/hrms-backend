package com.hrms.employee.service.impl;

import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.DocumentTypeRequest;
import com.hrms.employee.dto.response.DocumentTypeResponse;
import com.hrms.employee.entity.DocumentType;
import com.hrms.employee.repository.DocumentTypeRepository;
import com.hrms.employee.service.DocumentTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentTypeServiceImpl implements DocumentTypeService {

    private final DocumentTypeRepository docTypeRepository;

    @Override
    public List<DocumentTypeResponse> getAllDocumentTypes() {
        return docTypeRepository.findAllByOrderBySortOrderAsc()
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<DocumentTypeResponse> getActiveDocumentTypes() {
        return docTypeRepository.findByIsActiveOrderBySortOrderAsc(1)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<DocumentTypeResponse> getMandatoryDocumentTypes() {
        return docTypeRepository.findByIsMandatoryAndIsActiveOrderBySortOrderAsc(1, 1)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public DocumentTypeResponse getDocumentTypeById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public DocumentTypeResponse createDocumentType(DocumentTypeRequest request) {
        log.info("Creating document type: {}", request.getDocTypeCode());

        if (docTypeRepository.existsByDocTypeCodeIgnoreCase(request.getDocTypeCode())) {
            throw new DuplicateResourceException("DocumentType", "code", request.getDocTypeCode());
        }
        if (docTypeRepository.existsByDocTypeNameIgnoreCase(request.getDocTypeName())) {
            throw new DuplicateResourceException("DocumentType", "name", request.getDocTypeName());
        }

        DocumentType entity = DocumentType.builder()
            .docTypeCode(request.getDocTypeCode().toUpperCase().trim())
            .docTypeName(request.getDocTypeName().trim())
            .docTypeNameAr(request.getDocTypeNameAr().trim())
            .category(request.getCategory().toUpperCase())
            .description(request.getDescription())
            .isMandatory(boolToInt(request.getIsMandatory()))
            .hasExpiry(boolToInt(request.getHasExpiry()))
            .expiryNoticeDays(request.getExpiryNoticeDays() != null ? request.getExpiryNoticeDays() : 30)
            .allowedExtensions(request.getAllowedExtensions() != null
                ? request.getAllowedExtensions().toUpperCase() : "PDF,JPG,PNG")
            .maxFileSizeMb(request.getMaxFileSizeMb() != null ? request.getMaxFileSizeMb() : 5)
            .isActive(request.getIsActive() != null ? boolToInt(request.getIsActive()) : 1)
            .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
            .createdBy("SYSTEM")
            .createdAt(LocalDateTime.now())
            .build();

        DocumentType saved = docTypeRepository.save(entity);
        log.info("Document type created. ID: {}", saved.getDocTypeId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public DocumentTypeResponse updateDocumentType(Long id, DocumentTypeRequest request) {
        log.info("Updating document type id: {}", id);
        DocumentType existing = findById(id);

        if (docTypeRepository.existsByDocTypeCodeIgnoreCaseAndDocTypeIdNot(
                request.getDocTypeCode(), id)) {
            throw new DuplicateResourceException("DocumentType", "code", request.getDocTypeCode());
        }
        if (docTypeRepository.existsByDocTypeNameIgnoreCaseAndDocTypeIdNot(
                request.getDocTypeName(), id)) {
            throw new DuplicateResourceException("DocumentType", "name", request.getDocTypeName());
        }

        existing.setDocTypeCode(request.getDocTypeCode().toUpperCase().trim());
        existing.setDocTypeName(request.getDocTypeName().trim());
        existing.setDocTypeNameAr(request.getDocTypeNameAr().trim());
        existing.setCategory(request.getCategory().toUpperCase());
        existing.setDescription(request.getDescription());
        existing.setIsMandatory(boolToInt(request.getIsMandatory()));
        existing.setHasExpiry(boolToInt(request.getHasExpiry()));
        existing.setExpiryNoticeDays(request.getExpiryNoticeDays() != null ? request.getExpiryNoticeDays() : 30);
        existing.setAllowedExtensions(request.getAllowedExtensions() != null
            ? request.getAllowedExtensions().toUpperCase() : "PDF,JPG,PNG");
        existing.setMaxFileSizeMb(request.getMaxFileSizeMb() != null ? request.getMaxFileSizeMb() : 5);
        existing.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        if (request.getIsActive() != null) existing.setIsActive(boolToInt(request.getIsActive()));
        existing.setUpdatedBy("SYSTEM");
        existing.setUpdatedAt(LocalDateTime.now());

        return toResponse(docTypeRepository.save(existing));
    }

    @Override @Transactional
    public void deactivateDocumentType(Long id) {
        DocumentType dt = findById(id);
        dt.setIsActive(0); dt.setUpdatedAt(LocalDateTime.now());
        docTypeRepository.save(dt);
    }

    @Override @Transactional
    public void activateDocumentType(Long id) {
        DocumentType dt = docTypeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentType", "id", id));
        dt.setIsActive(1); dt.setUpdatedAt(LocalDateTime.now());
        docTypeRepository.save(dt);
    }

    private DocumentType findById(Long id) {
        return docTypeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("DocumentType", "id", id));
    }

    private DocumentTypeResponse toResponse(DocumentType dt) {
        return DocumentTypeResponse.builder()
            .docTypeId(dt.getDocTypeId())
            .docTypeCode(dt.getDocTypeCode())
            .docTypeName(dt.getDocTypeName())
            .docTypeNameAr(dt.getDocTypeNameAr())
            .category(dt.getCategory())
            .description(dt.getDescription())
            .isMandatory(dt.getIsMandatory() == 1)
            .hasExpiry(dt.getHasExpiry() == 1)
            .expiryNoticeDays(dt.getExpiryNoticeDays())
            .allowedExtensions(dt.getAllowedExtensions())
            .maxFileSizeMb(dt.getMaxFileSizeMb())
            .isActive(dt.getIsActive() == 1)
            .sortOrder(dt.getSortOrder())
            .createdBy(dt.getCreatedBy())
            .createdAt(dt.getCreatedAt() != null ? dt.getCreatedAt().toString() : null)
            .updatedAt(dt.getUpdatedAt() != null ? dt.getUpdatedAt().toString() : null)
            .build();
    }

    private int boolToInt(Boolean b) { return Boolean.TRUE.equals(b) ? 1 : 0; }
}
