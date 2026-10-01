package com.hrms.employee.config;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks that an upload claiming to be a Word (.docx) file really is one. A .docx is a ZIP archive
 * whose central directory lists {@code [Content_Types].xml} and {@code word/document.xml}; a
 * renamed ZIP of anything else does not.
 *
 * <p>Only the archive's table of contents is read. No entry is decompressed, so a crafted archive
 * cannot make the check expensive.
 */
public final class OfficePackage {

    private static final int END_OF_CENTRAL_DIRECTORY = 0x06054b50;
    private static final int CENTRAL_DIRECTORY_ENTRY = 0x02014b50;
    private static final int END_RECORD_LENGTH = 22;
    private static final int ENTRY_HEADER_LENGTH = 46;
    private static final int MAX_COMMENT_LENGTH = 0xFFFF;
    private static final int MAX_ENTRIES = 5000;

    private OfficePackage() {}

    public static boolean isWordDocument(byte[] zip) {
        Set<String> names = entryNames(zip);
        return names.contains("[Content_Types].xml") && names.contains("word/document.xml");
    }

    /** The file names listed in the archive's central directory; empty when it is not a ZIP. */
    static Set<String> entryNames(byte[] zip) {
        Set<String> names = new HashSet<>();
        if (zip == null || zip.length < END_RECORD_LENGTH) return names;

        ByteBuffer buffer = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        int end = -1;
        int lowest = Math.max(0, zip.length - END_RECORD_LENGTH - MAX_COMMENT_LENGTH);
        for (int i = zip.length - END_RECORD_LENGTH; i >= lowest; i--) {
            if (buffer.getInt(i) == END_OF_CENTRAL_DIRECTORY) {
                end = i;
                break;
            }
        }
        if (end < 0) return names;

        int count = Math.min(buffer.getShort(end + 10) & 0xFFFF, MAX_ENTRIES);
        long offset = buffer.getInt(end + 16) & 0xFFFFFFFFL;
        if (offset >= zip.length) return names;

        int position = (int) offset;
        for (int n = 0; n < count; n++) {
            if (position + ENTRY_HEADER_LENGTH > zip.length
                    || buffer.getInt(position) != CENTRAL_DIRECTORY_ENTRY) {
                break;
            }
            int nameLength = buffer.getShort(position + 28) & 0xFFFF;
            int extraLength = buffer.getShort(position + 30) & 0xFFFF;
            int commentLength = buffer.getShort(position + 32) & 0xFFFF;
            if (position + ENTRY_HEADER_LENGTH + nameLength > zip.length) break;
            names.add(new String(zip, position + ENTRY_HEADER_LENGTH, nameLength, StandardCharsets.UTF_8));
            position += ENTRY_HEADER_LENGTH + nameLength + extraLength + commentLength;
        }
        return names;
    }
}
