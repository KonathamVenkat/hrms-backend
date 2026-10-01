package com.hrms.leave.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.config.UploadHeader;
import com.hrms.employee.config.UploadLimits;
import com.hrms.leave.entity.LeaveType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Validates the supporting document attached to a leave request (e.g. a medical certificate)
 * and hands back its bytes; the caller saves them to the database with the request.
 * Validation mirrors the employee-document upload:
 * the extension, declared content-type AND the file's leading bytes must agree,
 * because the first two are client-supplied and trivially spoofed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaveAttachmentStorage {

    /** A validated attachment: the bytes to store and the values kept on the leave request. */
    public record Prepared(byte[] bytes, String originalName, long sizeBytes) {}

    private final UploadLimits uploadLimits;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "application/pdf", "image/jpeg", "image/png");

    private static final Map<String, byte[][]> FILE_SIGNATURES = Map.of(
        "pdf",  new byte[][] { {0x25, 0x50, 0x44, 0x46} },                                // %PDF
        "jpg",  new byte[][] { {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF} },
        "jpeg", new byte[][] { {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF} },
        "png",  new byte[][] { {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A} });

    /**
     * Throws BusinessRuleException if the file is missing or fails any check.
     * The leave type's configured limits narrow the global PDF/JPG/JPEG/PNG whitelist
     * (and its size limit is never above the global upload cap).
     */
    public Prepared prepare(MultipartFile file, LeaveType leaveType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("NO_FILE", "No file provided.");
        }
        int maxMb = uploadLimits.effectiveMb(leaveType.resolveDocMaxFileSizeMb());
        if (file.getSize() > maxMb * 1024L * 1024L) {
            throw new BusinessRuleException("FILE_TOO_LARGE",
                "Attachment exceeds the maximum size of " + maxMb + " MB for this leave type.");
        }

        Set<String> typeExtensions = Arrays.stream(leaveType.resolveDocAllowedExtensions().split(","))
            .map(s -> s.trim().toLowerCase())
            .filter(ALLOWED_EXTENSIONS::contains)   // hard ceiling: only formats we can signature-check
            .collect(Collectors.toCollection(HashSet::new));
        // jpg and jpeg are the same format
        if (typeExtensions.contains("jpg") || typeExtensions.contains("jpeg")) {
            typeExtensions.addAll(Set.of("jpg", "jpeg"));
        }

        String originalName = file.getOriginalFilename();
        String extension    = extensionOf(originalName);
        if (!typeExtensions.contains(extension)) {
            throw new BusinessRuleException("FILE_TYPE_NOT_ALLOWED",
                "Unsupported file type for this leave type. Allowed: "
                    + String.join(", ", new TreeSet<>(typeExtensions)).toUpperCase() + ".");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessRuleException("FILE_TYPE_NOT_ALLOWED",
                "Unsupported file type. Allowed: PDF, JPG, JPEG, PNG.");
        }

        // Check the leading bytes first so a mislabelled file is rejected without being loaded.
        if (!matchesSignature(UploadHeader.read(file), extension)) {
            throw new BusinessRuleException("FILE_TYPE_NOT_ALLOWED",
                "The file's content does not match its extension.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read leave attachment: {}", e.getMessage());
            throw new BusinessRuleException("FILE_SAVE_ERROR",
                "Failed to save attachment. Please try again.");
        }

        return new Prepared(bytes, sanitizeName(originalName), file.getSize());
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /** Keeps only the file-name part and strips characters that break a Content-Disposition header. */
    private String sanitizeName(String filename) {
        String name = filename == null ? "attachment"
            : filename.substring(Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\r\\n\"]", "_");
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private boolean matchesSignature(byte[] bytes, String extension) {
        byte[][] signatures = FILE_SIGNATURES.get(extension);
        if (signatures == null) return false;
        for (byte[] sig : signatures) {
            if (bytes.length < sig.length) continue;
            boolean match = true;
            for (int i = 0; i < sig.length; i++) {
                if (bytes[i] != sig[i]) { match = false; break; }
            }
            if (match) return true;
        }
        return false;
    }
}
