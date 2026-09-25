package com.mrms.document.internal;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Client for the ClamAV daemon using its INSTREAM command: the file is sent
 * in chunks over TCP and scanned in memory, so it never touches a disk
 * before it is known to be clean.
 *
 * <pre>
 * -> zINSTREAM\0 | [len][bytes] ... | [0 0 0 0]
 * <- "stream: OK\0" or "stream: &lt;signature&gt; FOUND\0"
 * </pre>
 */
final class ClamdScanner implements VirusScanner {

    private static final int CHUNK = 8192;
    private static final int MAX_REPLY = 4096;

    private final String host;
    private final int port;
    private final int timeoutMillis;
    private final Clock clock;

    ClamdScanner(String host, int port, int timeoutMillis, Clock clock) {
        this.host = host;
        this.port = port;
        this.timeoutMillis = timeoutMillis;
        this.clock = clock;
    }

    @Override
    public Result scan(byte[] content) {
        String reply = exchange(content);
        String engine = "ClamAV (" + host + ":" + port + ")";
        if (reply.endsWith("OK") && !reply.contains("FOUND")) {
            return new Result(Status.CLEAN, null, engine, clock.instant());
        }
        if (reply.endsWith("FOUND")) {
            String signature = reply.substring(reply.indexOf(':') + 1, reply.length() - "FOUND".length()).trim();
            return new Result(Status.INFECTED, signature, engine, clock.instant());
        }
        throw new UnavailableException("Unexpected reply from ClamAV: " + reply, null);
    }

    /** Liveness check used by the health endpoint and at startup. */
    boolean ping() {
        try (Socket socket = connect()) {
            socket.getOutputStream().write("zPING\0".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return "PONG".equals(readReply(socket.getInputStream()));
        } catch (IOException e) {
            return false;
        }
    }

    private String exchange(byte[] content) {
        try (Socket socket = connect()) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for (int offset = 0; offset < content.length; offset += CHUNK) {
                int length = Math.min(CHUNK, content.length - offset);
                out.writeInt(length);
                out.write(content, offset, length);
            }
            out.writeInt(0);
            out.flush();
            return readReply(socket.getInputStream());
        } catch (IOException e) {
            throw new UnavailableException("ClamAV is not reachable at " + host + ":" + port, e);
        }
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMillis);
        socket.setSoTimeout(timeoutMillis);
        return socket;
    }

    private static String readReply(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != 0) {
            if (buffer.size() >= MAX_REPLY) {
                throw new IOException("Reply too long");
            }
            buffer.write(b);
        }
        return buffer.toString(StandardCharsets.US_ASCII).trim();
    }
}
