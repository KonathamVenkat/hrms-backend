package com.hrms.employee.config;

import com.hrms.common.exception.BusinessRuleException;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * The one place that decides how large an uploaded file (employee document or leave
 * attachment) may be. Documents are stored in the database, so this is a hard ceiling for
 * every upload; a document type / leave type may set a lower limit of its own, never a higher one.
 *
 * <p>To change it, set {@code hrms.upload.max-file-size-mb} (env {@code HRMS_UPLOAD_MAX_FILE_SIZE_MB})
 * and restart — no code change. The servlet multipart limits are derived from the same value.</p>
 */
@Component
public class UploadLimits {

    /** Headroom for the JSON metadata part that travels in the same multipart request. */
    private static final long REQUEST_OVERHEAD_MB = 5;

    private final int maxFileSizeMb;

    public UploadLimits(@Value("${hrms.upload.max-file-size-mb:25}") int maxFileSizeMb) {
        if (maxFileSizeMb < 1) {
            throw new IllegalStateException("hrms.upload.max-file-size-mb must be at least 1");
        }
        this.maxFileSizeMb = maxFileSizeMb;
    }

    public int getMaxFileSizeMb() {
        return maxFileSizeMb;
    }

    /** The limit that actually applies: the type's own limit, capped by the global one. */
    public int effectiveMb(int typeLimitMb) {
        return Math.min(typeLimitMb, maxFileSizeMb);
    }

    /** Rejects a per-type limit that is above the global ceiling (admin screens). */
    public void assertWithinCap(Integer typeLimitMb) {
        if (typeLimitMb != null && typeLimitMb > maxFileSizeMb) {
            throw new BusinessRuleException("FILE_SIZE_LIMIT_EXCEEDS_MAX",
                "Max file size cannot exceed " + maxFileSizeMb + " MB.");
        }
    }

    /**
     * Replaces Spring Boot's multipart defaults (which the properties file would otherwise
     * set separately) so the servlet limit can never disagree with {@link #getMaxFileSizeMb()}.
     */
    @Bean
    MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(maxFileSizeMb));
        factory.setMaxRequestSize(DataSize.ofMegabytes(maxFileSizeMb + REQUEST_OVERHEAD_MB));
        return factory.createMultipartConfig();
    }
}
