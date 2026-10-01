package com.hrms.employee.config;

import com.hrms.common.exception.BusinessRuleException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Reads just the first bytes of an upload so the file-type signature can be checked before the
 * whole file (up to the global upload cap) is loaded into memory.
 */
public final class UploadHeader {

    /** Longer than any signature the upload checks compare against (PNG's is 8 bytes). */
    public static final int LENGTH = 16;

    private UploadHeader() {}

    public static byte[] read(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] buffer = in.readNBytes(LENGTH);
            return buffer.length == LENGTH ? buffer : Arrays.copyOf(buffer, buffer.length);
        } catch (IOException e) {
            throw new BusinessRuleException("FILE_SAVE_ERROR", "Failed to read the uploaded file. Please try again.");
        }
    }
}
