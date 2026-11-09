package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * One TCP connection to the server.
 *
 * Protocol: every message is one line of JSON. We end ours with "\r\n" because
 * the C# server only treats a message as complete after it sees '\r' then '\n'
 * (a bare '\n' would never be recognised and the server would wait forever).
 * The server also ends its lines with "\r\n"; readLine() accepts that.
 * TCP is a byte stream, so one send is not one receive: readLine() keeps
 * collecting bytes until it sees '\n' (a message split in two pieces waits for
 * the rest), and leaves anything after '\n' in the buffer for the next call
 * (two messages in one receive are returned one by one).
 */
public class Connection implements AutoCloseable {
    private final Socket socket;
    private final BufferedReader in;
    private final OutputStream out;
    private volatile boolean closedByUs = false;

    public Connection(String host, int port) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = socket.getOutputStream();
    }

    /**
     * Sends one message as one line. Synchronized so that two threads can never
     * interleave their bytes in the middle of each other's message.
     */
    public synchronized void send(String json) throws IOException {
        out.write((json + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** Blocks until one full message line arrives. Returns null when the server closed the connection. */
    public String readLine() throws IOException {
        return in.readLine();
    }

    public String remote() {
        return socket.getRemoteSocketAddress().toString();
    }

    public String local() {
        return socket.getLocalSocketAddress().toString();
    }

    /** True when we closed the socket ourselves (quit), so a read error is expected. */
    public boolean closedByUs() {
        return closedByUs;
    }

    @Override
    public void close() throws IOException {
        closedByUs = true;
        socket.close();
    }
}
