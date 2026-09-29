package com.hrms.leave.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.leave.entity.LeaveType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Validates and stores the supporting document attached to a leave request
 * (e.g. a medical certificate). Validation mirrors the employee-document upload:
 * the extension, declared content-type AND the file's leading bytes must agree,
 * because the first two are client-supplied and trivially spoofed.
 */
@Slf4j
@Component
public class LeaveAttachmentStorage {

    /** Result of a successful store — the values persisted on the leave request. */
    public record Stored(String relativePath, String originalName, long sizeBytes) {}


    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "application/pdf", "image/jpeg", "image/png");

    private static final Map<String, byte[][]> FILE_SIGNATURES = Map.of(
        "pdf",  new byte[][] { {0x25, 0x50, 0x44, 0x46} },                                // %PDF
        "jpg",  new byte[][] { {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF} },
        "jpeg", new byte[][] { {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF} },
        "png",  new byte[][] { {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A} });

    @Value("${app.leave-upload.dir:uploads/leave-attachments}")
    private String uploadDir;

    /**
     * Throws BusinessRuleException if the file is missing or fails any check; writes nothing.
     * The leave type's configured limits narrow the global PDF/JPG/JPEG/PNG whitelist.
     */
    public Stored store(Long employeeId, MultipartFile file, LeaveType leaveType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("NO_FILE", "No file provided.");
        }
        int maxMb = leaveType.resolveDocMaxFileSizeMb();
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

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read leave attachment: {}", e.getMessage());
            throw new BusinessRuleException("FILE_SAVE_ERROR",
                "Failed to save attachment. Please try again.");
        }
        if (!matchesSignature(bytes, extension)) {
            throw new BusinessRuleException("FILE_TYPE_NOT_ALLOWED",
                "The file's content does not match its extension.");
        }

        String subDir     = "employee_" + employeeId;
        String storedName = UUID.randomUUID() + "." + extension;
        Path   targetDir  = Paths.get(uploadDir, subDir);
        try {
            Files.createDirectories(targetDir);
            Files.write(targetDir.resolve(storedName), bytes);
        } catch (IOException e) {
            log.error("Failed to store leave attachment: {}", e.getMessage());
            throw new BusinessRuleException("FILE_SAVE_ERROR",
                "Failed to save attachment. Please try again.");
        }
        return new Stored(subDir + "/" + storedName, sanitizeName(originalName), file.getSize());
    }

    /** Best-effort removal, used to roll back when the DB save fails after the file was written. */
    public void deleteQuietly(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not delete orphaned leave attachment {}: {}", relativePath, e.getMessage());
        }
    }

    public Resource load(String relativePath) {
        try {
            Resource resource = new UrlResource(resolve(relativePath).toUri());
            if (!resource.exists()) {
                throw new ResourceNotFoundException("LeaveAttachment", "path", relativePath);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new BusinessRuleException("FILE_ERROR", "Could not read attachment.");
        }
    }

    /** Resolves under the upload root and refuses anything that escapes it. */
    private Path resolve(String relativePath) {
        Path root     = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessRuleException("FILE_ERROR", "Invalid attachment path.");
        }
        return resolved;
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
