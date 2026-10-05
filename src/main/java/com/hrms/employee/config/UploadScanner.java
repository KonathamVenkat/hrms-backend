package com.hrms.employee.config;

import com.hrms.common.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Malware check for every uploaded file (employee documents, photos, leave attachments), done by a
 * ClamAV daemon ({@code clamd}) over its INSTREAM protocol: nothing is written to disk, and the
 * file is only stored if the scanner says it is clean.
 *
 * <p>Off by default so a development machine needs no ClamAV. In production set
 * {@code hrms.upload.scan.enabled=true} (env {@code HRMS_UPLOAD_SCAN_ENABLED}); the service is then
 * <b>fail-closed</b>: if the scanner cannot be reached, the upload is refused rather than let through
 * unscanned.</p>
 */
@Slf4j
@Component
public class UploadScanner {

    private static final int CHUNK = 64 * 1024;

    private final boolean enabled;
    private final String host;
    private final int port;
    private final int timeoutMs;

    public UploadScanner(@Value("${hrms.upload.scan.enabled:false}") boolean enabled,
                         @Value("${hrms.upload.scan.host:localhost}") String host,
                         @Value("${hrms.upload.scan.port:3310}") int port,
                         @Value("${hrms.upload.scan.timeout-ms:15000}") int timeoutMs) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
        if (!enabled) {
            log.warn("Upload malware scanning is OFF (hrms.upload.scan.enabled=false). Turn it on in production.");
        }
    }

    /** @throws BusinessRuleException when the file is infected, or cannot be scanned while scanning is on */
    public void assertClean(byte[] content) {
        if (!enabled) {
            return;
        }
        String reply;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for (int off = 0; off < content.length; off += CHUNK) {
                int len = Math.min(CHUNK, content.length - off);
                out.writeInt(len);                       // 4-byte big-endian chunk length
                out.write(content, off, len);
            }
            out.writeInt(0);                             // end of stream
            out.flush();
            reply = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).trim();
        } catch (IOException e) {
            log.error("Malware scanner unreachable at {}:{}: {}", host, port, e.getMessage());
            throw new BusinessRuleException("SCAN_UNAVAILABLE",
                "The file could not be scanned for malware right now. Try again later or contact IT.");
        }
        if (reply.endsWith("FOUND")) {
            log.warn("Upload rejected by malware scanner: {}", reply);
            throw new BusinessRuleException("MALWARE_DETECTED",
                "The file was rejected because it contains malware.");
        }
        if (!reply.endsWith("OK")) {
            log.error("Unexpected malware scanner reply: {}", reply);
            throw new BusinessRuleException("SCAN_UNAVAILABLE",
                "The file could not be scanned for malware right now. Try again later or contact IT.");
        }
    }
}
