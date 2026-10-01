package com.hrms.employee.config;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficePackageTest {

    private static byte[] zip(String... names) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write("<x/>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    @Test
    void aRealWordPackageIsAccepted() throws IOException {
        assertTrue(OfficePackage.isWordDocument(
            zip("[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/styles.xml")));
    }

    @Test
    void aRenamedZipOfSomethingElseIsRejected() throws IOException {
        assertFalse(OfficePackage.isWordDocument(zip("payload.exe", "readme.txt")));
    }

    @Test
    void otherOfficeFormatsAreNotWordDocuments() throws IOException {
        assertFalse(OfficePackage.isWordDocument(zip("[Content_Types].xml", "xl/workbook.xml")));
    }

    @Test
    void textThatOnlyStartsLikeAZipIsRejected() {
        byte[] fake = new byte[200];
        fake[0] = 0x50; fake[1] = 0x4B; fake[2] = 0x03; fake[3] = 0x04;
        assertFalse(OfficePackage.isWordDocument(fake));
    }

    @Test
    void aTruncatedArchiveIsRejected() throws IOException {
        byte[] whole = zip("[Content_Types].xml", "word/document.xml");
        assertFalse(OfficePackage.isWordDocument(Arrays.copyOf(whole, whole.length - 10)));
    }

    @Test
    void emptyAndNullInputAreRejected() {
        assertFalse(OfficePackage.isWordDocument(new byte[0]));
        assertFalse(OfficePackage.isWordDocument(null));
    }
}
