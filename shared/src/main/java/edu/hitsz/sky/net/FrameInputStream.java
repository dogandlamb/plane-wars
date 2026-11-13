package edu.hitsz.sky.net;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

/** The idle wait and the receive time for a started frame are separate deadlines. */
final class FrameInputStream extends FilterInputStream {
    private static final int IDLE_TIMEOUT_MILLIS = 12000;
    private static final long FRAME_TIMEOUT_NANOS = 3_000_000_000L;
    private final Socket socket;
    private long deadline;

    FrameInputStream(InputStream input, Socket socket) { super(input); this.socket = socket; }

    void beginFrame() throws IOException { deadline = 0; socket.setSoTimeout(IDLE_TIMEOUT_MILLIS); }

    @Override public int read() throws IOException {
        prepareRead();
        int value;
        try { value = in.read(); }
        catch (SocketTimeoutException timeout) { throw receiveTimeout(timeout); }
        if (value >= 0) receivedBytes();
        return value;
    }

    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
        if (length == 0) return 0;
        prepareRead();
        int count;
        try { count = in.read(bytes, offset, length); }
        catch (SocketTimeoutException timeout) { throw receiveTimeout(timeout); }
        if (count > 0) receivedBytes();
        return count;
    }

    private void prepareRead() throws IOException {
        if (deadline == 0) return;
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new SocketTimeoutException("Frame receive deadline exceeded");
        socket.setSoTimeout((int)Math.max(1, (remaining + 999_999L) / 1_000_000L));
    }
    private void receivedBytes() throws IOException {
        long now = System.nanoTime();
        if (deadline == 0) deadline = now + FRAME_TIMEOUT_NANOS;
        else if (now >= deadline) throw new SocketTimeoutException("Frame receive deadline exceeded");
    }
    private SocketTimeoutException receiveTimeout(SocketTimeoutException timeout) {
        if (deadline == 0) return timeout;
        SocketTimeoutException expired = new SocketTimeoutException("Frame receive deadline exceeded");
        expired.initCause(timeout); return expired;
    }
}
