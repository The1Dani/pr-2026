package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The client and the provider: one program that starts in either role.
 * Usage: java org.example.Main <host> <port> [client|provider]
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java org.example.Main <host> <port> [client|provider]");
            return;
        }
        String host = args[0];
        int port;
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("The port must be a number: " + args[1]);
            return;
        }
        String role = args.length >= 3 ? args[2].toLowerCase() : "client";
        if (!role.equals("client") && !role.equals("provider")) {
            System.out.println("The role must be client or provider.");
            return;
        }

        Connection connection;
        try {
            connection = new Connection(host, port);
        } catch (IOException e) {
            System.out.println("Cannot connect to " + host + ":" + port + ": " + e.getMessage());
            return;
        }
        Console.info("Connected to " + connection.remote() + " from " + connection.local() + " as " + role);

        // First message: tell the server who we are.
        try {
            Map<String, Object> hello = new LinkedHashMap<>();
            hello.put("role", role);
            connection.send(Json.object(hello));
        } catch (IOException e) {
            Console.info("Cannot send the role: " + e.getMessage());
            return;
        }

        // Receive on a second thread, so server messages show up while the user types.
        // A provider also answers restock requests automatically on that thread.
        Provider provider = role.equals("provider") ? new Provider(connection) : null;
        Thread reader = new Thread(new MessageReader(connection, provider), "message-reader");
        reader.start();

        Console.info(CommandParser.help(role));
        keyboardLoop(connection, role);
    }

    private static void keyboardLoop(Connection connection, String role) {
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        try {
            while (true) {
                Console.prompt();
                String input = keyboard.readLine();
                if (input == null) break;                 // Ctrl+D / end of input
                input = input.strip();
                if (input.isEmpty()) continue;
                if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) break;
                if (input.equalsIgnoreCase("help")) {
                    Console.info(CommandParser.help(role));
                    continue;
                }

                String json;
                try {
                    json = CommandParser.parse(input, role);
                } catch (IllegalArgumentException e) {
                    Console.info(e.getMessage());
                    continue;
                }
                connection.send(json);
            }
        } catch (IOException e) {
            Console.info("Cannot send to the server: " + e.getMessage());
        }

        try {
            connection.close();
        } catch (IOException ignored) {
        }
        Console.info("Bye.");
        System.exit(0);
    }
}
