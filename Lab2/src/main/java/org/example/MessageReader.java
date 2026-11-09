package org.example;

import java.io.IOException;

/**
 * Runs on its own thread and prints every message from the server as soon as
 * it arrives, while the main thread is blocked reading the keyboard.
 * For a provider it also passes each message to the Provider, which answers
 * restock requests automatically.
 * When the server disappears, it ends the whole program cleanly.
 */
public class MessageReader implements Runnable {
    private final Connection connection;
    private final Provider provider;   // null when the role is client

    public MessageReader(Connection connection, Provider provider) {
        this.connection = connection;
        this.provider = provider;
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = connection.readLine()) != null) {
                Console.server(line);
                if (provider != null) provider.onServerMessage(line);
            }
            // readLine() returned null: the server closed the connection normally (receive returned 0).
            if (!connection.closedByUs()) {
                Console.info("\nThe server closed the connection.");
            }
        } catch (IOException e) {
            // Connection reset or similar; expected if we closed the socket ourselves.
            if (!connection.closedByUs()) {
                Console.info("\nConnection to the server lost: " + e.getMessage());
            }
        }
        System.exit(0);
    }
}
