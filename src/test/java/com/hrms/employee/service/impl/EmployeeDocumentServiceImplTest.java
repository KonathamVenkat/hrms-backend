package com.hrms.employee.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.config.UploadLimits;
import com.hrms.employee.dto.request.EmployeeDocumentRequest;
import com.hrms.employee.entity.DocumentType;
import com.hrms.employee.entity.EmployeeDocument;
import com.hrms.employee.entity.EmployeeDocumentContent;
import com.hrms.employee.repository.DocumentTypeRepository;
import com.hrms.employee.repository.EmployeeDocumentContentRepository;
import com.hrms.employee.repository.EmployeeDocumentRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.repository.projection.EmployeeDocumentProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmployeeDocumentServiceImplTest {

    private static final byte[] PDF = "%PDF-1.7 test".getBytes();

    EmployeeDocumentRepository        documents = mock(EmployeeDocumentRepository.class);
    DocumentTypeRepository            docTypes  = mock(DocumentTypeRepository.class);
    EmployeeRepository                employees = mock(EmployeeRepository.class);
    EmployeeDocumentContentRepository contents  = mock(EmployeeDocumentContentRepository.class);
    EmployeeDocumentServiceImpl service;
    DocumentType passportType;

    @BeforeEach
    void setUp() {
        service = new EmployeeDocumentServiceImpl(documents, docTypes, employees, contents, new UploadLimits(25),
                new com.hrms.employee.config.UploadScanner(false, "localhost", 3310, 1000));
        when(employees.existsById(5L)).thenReturn(true);

        passportType = DocumentType.builder()
            .docTypeId(3L).allowedExtensions("PDF,JPG").maxFileSizeMb(1).isActive(1).build();
        when(docTypes.findById(3L)).thenReturn(Optional.of(passportType));

        when(documents.saveAndFlush(any())).thenAnswer(i -> {
            EmployeeDocument d = i.getArgument(0);
            d.setDocumentId(9L);
            return d;
        });
        EmployeeDocumentProjection projection = mock(EmployeeDocumentProjection.class);
        when(documents.findByDocumentIdAndEmployeeId(9L, 5L)).thenReturn(Optional.of(projection));
    }

    private static EmployeeDocumentRequest request() {
        return EmployeeDocumentRequest.builder().docTypeId(3L).documentName(" Passport ").build();
    }

    private static MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/pdf", bytes);
    }

    private static EmployeeDocument stored(long id, long employeeId, int active) {
        return EmployeeDocument.builder()
            .documentId(id).employeeId(employeeId).originalFileName("passport.pdf").isActive(active).build();
    }

    // ── Upload ──────────────────────────────────────────────

    @Test
    void storesTheMetadataAndTheBytesInTheDatabase() {
        service.uploadDocument(5L, request(), file("passport.pdf", PDF));

        ArgumentCaptor<EmployeeDocument> meta = ArgumentCaptor.forClass(EmployeeDocument.class);
        verify(documents).saveAndFlush(meta.capture());
        assertEquals("Passport", meta.getValue().getDocumentName());
        assertEquals("PDF", meta.getValue().getFileExtension());
        assertEquals(0, meta.getValue().getIsVerified());

        ArgumentCaptor<EmployeeDocumentContent> content = ArgumentCaptor.forClass(EmployeeDocumentContent.class);
        verify(contents).save(content.capture());
        assertEquals(9L, content.getValue().getDocumentId());
        assertArrayEquals(PDF, content.getValue().getContent());
    }

    @Test
    void rejectsAnInactiveDocumentType() {
        passportType.setIsActive(0);

        var ex = assertThrows(BusinessRuleException.class,
            () -> service.uploadDocument(5L, request(), file("passport.pdf", PDF)));

        assertEquals("DOCUMENT_TYPE_INACTIVE", ex.getRuleCode());
    }

    @Test
    void rejectsAMissingOrEmptyFile() {
        var none = assertThrows(BusinessRuleException.class, () -> service.uploadDocument(5L, request(), null));
        var empty = assertThrows(BusinessRuleException.class,
            () -> service.uploadDocument(5L, request(), file("passport.pdf", new byte[0])));

        assertEquals("NO_FILE", none.getRuleCode());
        assertEquals("NO_FILE", empty.getRuleCode());
    }

    @Test
    void rejectsAFileOverTheLimitOfItsDocumentType() {
        byte[] big = Arrays.copyOf(PDF, 1024 * 1024 + 1);

        var ex = assertThrows(BusinessRuleException.class,
            () -> service.uploadDocument(5L, request(), file("passport.pdf", big)));

        assertEquals("FILE_TOO_LARGE", ex.getRuleCode());
        verify(contents, never()).save(any());
    }

    @Test
    void rejectsAnExtensionTheDocumentTypeDoesNotAllow() {
        // PNG is on the global list, but this document type only allows PDF and JPG.
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

        var ex = assertThrows(BusinessRuleException.class,
            () -> service.uploadDocument(5L, request(), new MockMultipartFile("file", "scan.png", "image/png", png)));

        assertEquals("FILE_TYPE_NOT_ALLOWED", ex.getRuleCode());
    }

    @Test
    void rejectsAFileWhoseBytesDoNotMatchItsExtension() {
        byte[] exe = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};

        var ex = assertThrows(BusinessRuleException.class,
            () -> service.uploadDocument(5L, request(), file("passport.pdf", exe)));

        assertEquals("FILE_TYPE_NOT_ALLOWED", ex.getRuleCode());
        verify(contents, never()).save(any());
    }

    @Test
    void anUnknownEmployeeIsNotFound() {
        assertThrows(ResourceNotFoundException.class,
            () -> service.uploadDocument(9L, request(), file("passport.pdf", PDF)));
    }

    // ── Download / delete ───────────────────────────────────

    @Test
    void downloadsTheStoredBytesUnderTheOriginalName() throws IOException {
        when(documents.findById(9L)).thenReturn(Optional.of(stored(9, 5, 1)));
        when(contents.findById(9L)).thenReturn(Optional.of(new EmployeeDocumentContent(9L, PDF)));

        Resource resource = service.downloadDocument(5L, 9L);

        assertEquals("passport.pdf", resource.getFilename());
        assertArrayEquals(PDF, resource.getContentAsByteArray());
    }

    @Test
    void aDocumentWithoutStoredContentIsNotFound() {
        when(documents.findById(9L)).thenReturn(Optional.of(stored(9, 5, 1)));
        when(contents.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.downloadDocument(5L, 9L));
    }

    @Test
    void aDeletedOrForeignDocumentCannotBeDownloadedOrVerified() {
        when(documents.findById(9L)).thenReturn(Optional.of(stored(9, 5, 0)));
        when(documents.findById(10L)).thenReturn(Optional.of(stored(10, 6, 1)));

        assertThrows(ResourceNotFoundException.class, () -> service.downloadDocument(5L, 9L));
        assertThrows(ResourceNotFoundException.class, () -> service.downloadDocument(5L, 10L));
        assertThrows(ResourceNotFoundException.class, () -> service.verifyDocument(5L, 10L, "admin"));
        verify(contents, never()).findById(any());
    }

    @Test
    void deleteIsASoftDelete() {
        EmployeeDocument doc = stored(9, 5, 1);
        when(documents.findById(9L)).thenReturn(Optional.of(doc));

        service.deleteDocument(5L, 9L);

        assertEquals(0, doc.getIsActive());
        verify(documents).save(doc);
        verify(contents, never()).deleteById(any());
    }
}
