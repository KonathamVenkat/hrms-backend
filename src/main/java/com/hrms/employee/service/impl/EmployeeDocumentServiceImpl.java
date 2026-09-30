package com.hrms.employee.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.EmployeeDocumentRequest;
import com.hrms.employee.dto.response.EmployeeDocumentResponse;
import com.hrms.employee.entity.DocumentType;
import com.hrms.employee.config.UploadLimits;
import com.hrms.employee.entity.EmployeeDocument;
import com.hrms.employee.entity.EmployeeDocumentContent;
import com.hrms.employee.repository.DocumentTypeRepository;
import com.hrms.employee.repository.EmployeeDocumentContentRepository;
import com.hrms.employee.repository.EmployeeDocumentRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.repository.projection.EmployeeDocumentProjection;
import com.hrms.employee.service.EmployeeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeDocumentServiceImpl implements EmployeeDocumentService {

    private final EmployeeDocumentRepository documentRepository;
    private final DocumentTypeRepository     docTypeRepository;
    private final EmployeeRepository         employeeRepository;
    private final EmployeeDocumentContentRepository contentRepository;
    private final UploadLimits               uploadLimits;

    @Value("${app.base-url:http://localhost:8082}")
    private String baseUrl;

    // ── Upload whitelist ───────────────────────────────────────
    // Mirrors the file picker's `accept` filter on the frontend (employee-documents.html),
    // which is advisory only — this is the actual enforcement point.
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "jpg", "jpeg", "png", "doc", "docx");

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "application/pdf",
        "image/jpeg",
        "image/png",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    // Magic-number signatures per extension — the declared extension/content-type are
    // client-supplied and trivially spoofed (e.g. renaming an .exe to .pdf), so the actual
    // file bytes are checked against the format they claim to be.
    private static final Map<String, byte[][]> FILE_SIGNATURES = Map.of(
        "pdf",  new byte[][] { {0x25, 0x50, 0x44, 0x46} },                               // %PDF
        "jpg",  new byte[][] { {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF} },
        "jpeg", new byte[][] { {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF} },
        "png",  new byte[][] { {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A} },
        "doc",  new byte[][] { {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0} },           // OLE compound
        "docx", new byte[][] { {0x50, 0x4B, 0x03, 0x04} });                               // ZIP/OOXML

    // ── Get all documents ─────────────────────────────────────
    @Override
    public List<EmployeeDocumentResponse> getDocuments(Long employeeId) {
        validateEmployee(employeeId);
        return documentRepository.findActiveByEmployee(employeeId)
            .stream().map(this::toResponse)
            .collect(Collectors.toList());
    }

    // ── Get single document ───────────────────────────────────
    @Override
    public EmployeeDocumentResponse getDocumentById(Long employeeId, Long documentId) {
        return toResponse(findProjection(employeeId, documentId));
    }

    // ── Upload ────────────────────────────────────────────────
    @Override
    @Transactional
    public EmployeeDocumentResponse uploadDocument(Long employeeId,
                                                    EmployeeDocumentRequest request,
                                                    MultipartFile file) {
        log.info("Uploading document for employee {}, type {}",
            employeeId, request.getDocTypeId());
        validateEmployee(employeeId);

        // ── Validate document type exists and is active ───────
        DocumentType docType = docTypeRepository.findById(request.getDocTypeId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "DocumentType", "id", request.getDocTypeId()));
        if (docType.getIsActive() != 1) {
            throw new BusinessRuleException("DOCUMENT_TYPE_INACTIVE",
                "This document type is no longer active and cannot accept uploads.");
        }

        // ── Validate file ─────────────────────────────────────
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("NO_FILE", "No file provided.");
        }

        String originalName = file.getOriginalFilename();
        String extensionLc  = getExtension(originalName).toLowerCase();
        String extension    = extensionLc.toUpperCase();
        long   sizeBytes    = file.getSize();

        // Max size guard — the document type's configured limit, never above the global cap
        // (hrms.upload.max-file-size-mb) even if the cap was lowered after the type was saved.
        long maxSizeBytes = uploadLimits.effectiveMb(
                docType.getMaxFileSizeMb() != null ? docType.getMaxFileSizeMb() : 5)
            * 1024L * 1024L;
        if (sizeBytes > maxSizeBytes) {
            throw new BusinessRuleException("FILE_TOO_LARGE",
                "File size exceeds the maximum allowed for this document type ("
                    + (maxSizeBytes / (1024 * 1024)) + " MB).");
        }

        // ── Extension / content-type / actual-content whitelist ─
        // Global list is the hard security ceiling (only formats we know how to signature-
        // check); the document type's own configured list narrows it further per type.
        Set<String> typeExtensions = docType.getAllowedExtensions() != null
            ? java.util.Arrays.stream(docType.getAllowedExtensions().split(","))
                .map(String::trim).map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet())
            : ALLOWED_EXTENSIONS;
        if (!ALLOWED_EXTENSIONS.contains(extensionLc) || !typeExtensions.contains(extensionLc)) {
            throw new BusinessRuleException("FILE_TYPE_NOT_ALLOWED",
                "Unsupported file type for this document type. Allowed: "
                    + String.join(", ", typeExtensions).toUpperCase() + ".");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessRuleException("FILE_TYPE_NOT_ALLOWED",
                "Unsupported file type. Allowed: PDF, JPG, JPEG, PNG, DOC, DOCX.");
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read uploaded file: {}", e.getMessage());
            throw new BusinessRuleException("FILE_SAVE_ERROR",
                "Failed to save file. Please try again.");
        }
        if (!matchesFileSignature(fileBytes, extensionLc)) {
            throw new BusinessRuleException("FILE_TYPE_NOT_ALLOWED",
                "The file's content does not match its extension.");
        }

        // ── Expiry date validation ────────────────────────────
        if (request.getIssueDate() != null && request.getExpiryDate() != null) {
            if (request.getExpiryDate().isBefore(request.getIssueDate())) {
                throw new BusinessRuleException("INVALID_DATES",
                    "Expiry date must be after issue date.");
            }
        }

        // ── Save metadata + file bytes to DB (same transaction) ─
        EmployeeDocument entity = EmployeeDocument.builder()
            .employeeId(employeeId)
            .docTypeId(request.getDocTypeId())
            .documentName(request.getDocumentName().trim())
            .originalFileName(originalName)
            .fileSize(sizeBytes)
            .fileExtension(extension)
            .documentNumber(clean(request.getDocumentNumber()))
            .issueDate(request.getIssueDate())
            .expiryDate(request.getExpiryDate())
            .issuedBy(clean(request.getIssuedBy()))
            .notes(clean(request.getNotes()))
            .isVerified(0)
            .isActive(1)
            .uploadedBy(getCurrentAuditor())
            .createdAt(LocalDateTime.now())
            .build();

        EmployeeDocument saved = documentRepository.saveAndFlush(entity);
        contentRepository.save(new EmployeeDocumentContent(saved.getDocumentId(), fileBytes));
        log.info("Document uploaded. ID: {}", saved.getDocumentId());

        return toResponse(findProjection(employeeId, saved.getDocumentId()));
    }

    // ── Update document metadata ──────────────────────────────
    @Override
    @Transactional
    public EmployeeDocumentResponse updateDocumentInfo(Long employeeId,
                                                        Long documentId,
                                                        EmployeeDocumentRequest request) {
        EmployeeDocument doc = findEntity(employeeId, documentId);

        doc.setDocumentName(request.getDocumentName().trim());
        doc.setDocumentNumber(clean(request.getDocumentNumber()));
        doc.setIssueDate(request.getIssueDate());
        doc.setExpiryDate(request.getExpiryDate());
        doc.setIssuedBy(clean(request.getIssuedBy()));
        doc.setNotes(clean(request.getNotes()));
        doc.setUpdatedAt(LocalDateTime.now());

        documentRepository.save(doc);
        return toResponse(findProjection(employeeId, documentId));
    }

    // ── Verify document ───────────────────────────────────────
    @Override
    @Transactional
    public EmployeeDocumentResponse verifyDocument(Long employeeId,
                                                    Long documentId,
                                                    String verifiedBy) {
        EmployeeDocument doc = findEntity(employeeId, documentId);
        doc.setIsVerified(1);
        doc.setVerifiedBy(verifiedBy);
        doc.setVerifiedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(doc);
        log.info("Document {} verified by {}", documentId, verifiedBy);
        return toResponse(findProjection(employeeId, documentId));
    }

    // ── Delete (soft) ─────────────────────────────────────────
    @Override
    @Transactional
    public void deleteDocument(Long employeeId, Long documentId) {
        EmployeeDocument doc = findEntity(employeeId, documentId);
        doc.setIsActive(0);
        doc.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(doc);
        log.info("Document {} soft-deleted for employee {}", documentId, employeeId);
    }

    // ── Download ──────────────────────────────────────────────
    @Override
    public Resource downloadDocument(Long employeeId, Long documentId) {
        EmployeeDocument doc = findEntity(employeeId, documentId);
        byte[] bytes = contentRepository.findById(documentId)
            .orElseThrow(() -> new BusinessRuleException("FILE_NOT_FOUND",
                "File content not found for this document."))
            .getContent();
        String fileName = doc.getOriginalFileName();
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    // ── Private helpers ───────────────────────────────────────

    private void validateEmployee(Long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee", "id", employeeId);
        }
    }

    private EmployeeDocument findEntity(Long employeeId, Long documentId) {
        EmployeeDocument doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));
        // A soft-deleted document is gone as far as callers are concerned — it must not
        // still be downloadable, verifiable or editable by guessing its id.
        if (!doc.getEmployeeId().equals(employeeId)
                || !Integer.valueOf(1).equals(doc.getIsActive())) {
            throw new ResourceNotFoundException("Document", "id", documentId);
        }
        return doc;
    }

    private String getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return "SYSTEM";
        return auth.getName();
    }

    private EmployeeDocumentProjection findProjection(Long employeeId, Long documentId) {
        return documentRepository.findByDocumentIdAndEmployeeId(documentId, employeeId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));
    }

    private EmployeeDocumentResponse toResponse(EmployeeDocumentProjection p) {
        LocalDate today    = LocalDate.now();
        LocalDate in30Days = today.plusDays(30);

        LocalDate expiryDate = p.getExpiryDate() != null
            ? LocalDate.parse(p.getExpiryDate()) : null;

        boolean isExpired     = expiryDate != null && expiryDate.isBefore(today);
        boolean expiringSoon  = expiryDate != null && !isExpired
            && !expiryDate.isAfter(in30Days);

        return EmployeeDocumentResponse.builder()
            .documentId(p.getDocumentId())
            .employeeId(p.getEmployeeId())
            .docTypeId(p.getDocTypeId())
            .docTypeCode(p.getDocTypeCode())
            .docTypeName(p.getDocTypeName())
            .category(p.getCategory())
            .hasExpiry(p.getHasExpiry() == 1)
            .documentName(p.getDocumentName())
            .originalFileName(p.getOriginalFileName())
            .fileSize(p.getFileSize())
            .fileExtension(p.getFileExtension())
            .fileSizeFormatted(formatFileSize(p.getFileSize()))
            .downloadUrl(baseUrl + "/api/v1/employees/"
                + p.getEmployeeId() + "/documents/"
                + p.getDocumentId() + "/download")
            .documentNumber(p.getDocumentNumber())
            .issueDate(p.getIssueDate())
            .expiryDate(p.getExpiryDate())
            .issuedBy(p.getIssuedBy())
            .notes(p.getNotes())
            .isVerified(p.getIsVerified() == 1)
            .verifiedBy(p.getVerifiedBy())
            .verifiedAt(p.getVerifiedAt())
            .isExpired(isExpired)
            .isExpiringSoon(expiringSoon)
            .uploadedBy(p.getUploadedBy())
            .createdAt(p.getCreatedAt())
            .build();
    }

    private String formatFileSize(Long bytes) {
        if (bytes == null) return "0 B";
        if (bytes < 1024)             return bytes + " B";
        if (bytes < 1024 * 1024)      return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "bin";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    /** Confirms the file's actual leading bytes match one of the known signatures for `extensionLc`. */
    private boolean matchesFileSignature(byte[] fileBytes, String extensionLc) {
        byte[][] signatures = FILE_SIGNATURES.get(extensionLc);
        if (signatures == null) return false;
        for (byte[] sig : signatures) {
            if (fileBytes.length < sig.length) continue;
            boolean match = true;
            for (int i = 0; i < sig.length; i++) {
                if (fileBytes[i] != sig[i]) { match = false; break; }
            }
            if (match) return true;
        }
        return false;
    }

    private String clean(String val) {
        return (val != null && !val.isBlank()) ? val.trim() : null;
    }
}
