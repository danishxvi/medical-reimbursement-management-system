package com.mrms.document.internal;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the clamd INSTREAM protocol against a tiny fake daemon, so no
 * real virus signature (not even the EICAR test string) is needed.
 */
class ClamdScannerTests {

    @Test
    void cleanFileIsReportedClean() throws Exception {
        AtomicReference<byte[]> received = new AtomicReference<>();
        try (FakeClamd clamd = new FakeClamd("stream: OK", received)) {
            byte[] file = new byte[20_000];
            file[19_999] = 7;
            VirusScanner.Result result = scanner(clamd.port()).scan(file);
            assertThat(result.status()).isEqualTo(VirusScanner.Status.CLEAN);
            assertThat(result.engine()).startsWith("ClamAV");
            // The file arrived complete, reassembled from several chunks
            assertThat(received.get()).isEqualTo(file);
        }
    }

    @Test
    void infectedFileReportsTheSignature() throws Exception {
        try (FakeClamd clamd = new FakeClamd("stream: Win.Test.Fake-1 FOUND", new AtomicReference<>())) {
            VirusScanner.Result result = scanner(clamd.port()).scan("x".getBytes(StandardCharsets.US_ASCII));
            assertThat(result.status()).isEqualTo(VirusScanner.Status.INFECTED);
            assertThat(result.signature()).isEqualTo("Win.Test.Fake-1");
        }
    }

    @Test
    void unreachableScannerIsAnError() throws Exception {
        int freePort;
        try (ServerSocket s = new ServerSocket(0)) {
            freePort = s.getLocalPort();
        }
        ClamdScanner scanner = scanner(freePort);
        assertThatThrownBy(() -> scanner.scan(new byte[]{1}))
                .isInstanceOf(VirusScanner.UnavailableException.class);
        assertThat(scanner.ping()).isFalse();
    }

    private static ClamdScanner scanner(int port) {
        return new ClamdScanner("127.0.0.1", port, 3000, Clock.systemUTC());
    }

    /** Answers one INSTREAM request with a fixed reply. */
    private static final class FakeClamd implements AutoCloseable {

        private final ServerSocket server;
        private final Thread thread;

        FakeClamd(String reply, AtomicReference<byte[]> received) throws IOException {
            server = new ServerSocket(0);
            thread = new Thread(() -> {
                try (Socket s = server.accept()) {
                    DataInputStream in = new DataInputStream(s.getInputStream());
                    byte[] command = new byte["zINSTREAM\0".length()];
                    in.readFully(command);
                    ByteArrayOutputStream body = new ByteArrayOutputStream();
                    for (int len = in.readInt(); len > 0; len = in.readInt()) {
                        byte[] chunk = new byte[len];
                        in.readFully(chunk);
                        body.write(chunk);
                    }
                    received.set(body.toByteArray());
                    s.getOutputStream().write((reply + "\0").getBytes(StandardCharsets.US_ASCII));
                    s.getOutputStream().flush();
                } catch (IOException ignored) {
                    // test ends
                }
            });
            thread.start();
        }

        int port() {
            return server.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            server.close();
            thread.join(2000);
        }
    }
}
