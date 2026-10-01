package com.hrms.employee.config;

import com.hrms.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class UploadHeaderTest {

    @Test
    void readsOnlyTheLeadingBytes() {
        byte[] content = new byte[10_000];
        content[0] = 0x25; content[1] = 0x50;
        byte[] header = UploadHeader.read(new MockMultipartFile("f", "a.pdf", "application/pdf", content));

        assertEquals(UploadHeader.LENGTH, header.length);
        assertEquals(0x25, header[0]);
    }

    @Test
    void shortFilesReturnWhatTheyHave() {
        byte[] header = UploadHeader.read(new MockMultipartFile("f", "a.pdf", "application/pdf", new byte[] {1, 2, 3}));
        assertEquals(3, header.length);
    }

    @Test
    void unreadableUploadIsABusinessError() {
        var broken = new MockMultipartFile("f", "a.pdf", "application/pdf", new byte[] {1}) {
            @Override public java.io.InputStream getInputStream() throws java.io.IOException {
                throw new java.io.IOException("disk gone");
            }
        };
        assertThrows(BusinessRuleException.class, () -> UploadHeader.read(broken));
    }
}
