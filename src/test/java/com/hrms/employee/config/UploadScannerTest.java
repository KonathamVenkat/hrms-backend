package com.hrms.employee.config;

import com.hrms.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the scanner against a tiny stand-in for clamd that speaks the INSTREAM protocol. */
class UploadScannerTest {

    /** Starts a one-shot fake clamd that replies with {@code reply}; the bytes it received end up in {@code received}. */
    private static int fakeClamd(String reply, ByteArrayOutputStream received) throws IOException {
        ServerSocket server = new ServerSocket(0);
        Thread t = new Thread(() -> {
            try (server; var socket = server.accept()) {
                DataInputStream in = new DataInputStream(socket.getInputStream());
                in.readNBytes("zINSTREAM\0".length());
                for (int len; (len = in.readInt()) > 0; ) {
                    received.write(in.readNBytes(len));
                }
                socket.getOutputStream().write((reply + "\0").getBytes(StandardCharsets.US_ASCII));
            } catch (IOException ignored) {
                // the client side of the test reports the failure
            }
        });
        t.setDaemon(true);
        t.start();
        return server.getLocalPort();
    }

    private static UploadScanner scanner(int port) {
        return new UploadScanner(true, "localhost", port, 3000);
    }

    @Test
    void aCleanFilePassesAndTheScannerReceivesExactlyTheBytes() throws IOException {
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        byte[] file = new byte[200_000];                    // larger than one chunk
        java.util.Arrays.fill(file, (byte) 7);

        scanner(fakeClamd("stream: OK", received)).assertClean(file);

        assertArrayEquals(file, received.toByteArray());
    }

    @Test
    void anInfectedFileIsRejected() throws IOException {
        var ex = assertThrows(BusinessRuleException.class, () ->
            scanner(fakeClamd("stream: Eicar-Test-Signature FOUND", new ByteArrayOutputStream()))
                .assertClean("x".getBytes()));

        assertEquals("MALWARE_DETECTED", ex.getRuleCode());
    }

    @Test
    void whenTheScannerCannotBeReachedTheUploadIsRefused() throws IOException {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) { closedPort = s.getLocalPort(); }

        var ex = assertThrows(BusinessRuleException.class, () -> scanner(closedPort).assertClean(new byte[] {1}));

        assertEquals("SCAN_UNAVAILABLE", ex.getRuleCode());
    }

    @Test
    void anUnexpectedReplyIsNotTreatedAsClean() throws IOException {
        var ex = assertThrows(BusinessRuleException.class, () ->
            scanner(fakeClamd("INSTREAM size limit exceeded. ERROR", new ByteArrayOutputStream()))
                .assertClean(new byte[] {1}));

        assertEquals("SCAN_UNAVAILABLE", ex.getRuleCode());
    }

    @Test
    void whenSwitchedOffNothingIsContacted() {
        assertDoesNotThrow(() -> new UploadScanner(false, "nowhere.invalid", 1, 100).assertClean(new byte[] {1}));
    }
}
