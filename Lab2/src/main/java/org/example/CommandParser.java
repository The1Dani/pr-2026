package org.example;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns what the user types into one JSON line for the server.
 *
 *   menu                            -> {"action":"menu"}
 *   order coffee=1 tea=2            -> {"action":"order","coffee":1,"tea":2}
 *   deliver coffee=20 croissant=10  -> {"action":"delivery","coffee":20,"croissant":10}
 *   {"anything":"raw"}              -> sent unchanged (to test the server's error handling)
 *
 * It only checks the shape of the command. Product names and quantities are
 * checked by the server. Note: the C# server answers some bad input with
 * {"Error":"..."} (e.g. quantity <= 0, not enough stock), but an unknown product,
 * a non-number quantity or invalid JSON makes it close the connection.
 */
public class CommandParser {

    private static final String CLIENT_HELP = """
            Commands (client):
              menu                              show products and stock
              order <product>=<qty> ...         e.g. order coffee=1 tea=2
              {...}                             send a raw JSON line as-is
              help                              show this help
              quit                              disconnect and exit""";

    private static final String PROVIDER_HELP = """
            Commands (provider):
              deliver <product>=<qty> ...       e.g. deliver coffee=20
              {...}                             send a raw JSON line as-is
              help                              show this help
              quit                              disconnect and exit
            Restock requests from the server are delivered automatically.""";

    public static String help(String role) {
        return role.equals("provider") ? PROVIDER_HELP : CLIENT_HELP;
    }

    /**
     * Returns the JSON line to send. Throws IllegalArgumentException with a message for bad input.
     * Commands are checked against the role, because the server silently ignores
     * an action the role does not have (e.g. menu from a provider), so no answer would come.
     */
    public static String parse(String input, String role) {
        String line = input.strip();
        if (line.startsWith("{")) {
            return line;
        }

        String[] parts = line.split("\\s+");
        String command = parts[0].toLowerCase();

        boolean provider = role.equals("provider");
        if (provider && (command.equals("menu") || command.equals("order"))) {
            throw new IllegalArgumentException(command + " is a client command; a provider can only deliver.");
        }
        if (!provider && (command.equals("deliver") || command.equals("delivery"))) {
            throw new IllegalArgumentException(command + " is a provider command; connect as provider to use it.");
        }

        return switch (command) {
            case "menu" -> {
                if (parts.length > 1) throw new IllegalArgumentException("menu takes no parameters.");
                yield Json.action("menu");
            }
            case "order" -> withItems("order", parts);
            // The server's provider action is called "delivery".
            case "deliver", "delivery" -> withItems("delivery", parts);
            default -> throw new IllegalArgumentException("Unknown command: " + parts[0] + " (type help).");
        };
    }

    private static String withItems(String action, String[] parts) {
        if (parts.length < 2) {
            throw new IllegalArgumentException(parts[0] + " needs at least one item, e.g. " + parts[0] + " coffee=1");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("action", action);
        for (int i = 1; i < parts.length; i++) {
            String[] kv = parts[i].split("=", -1);
            if (kv.length != 2 || kv[0].isEmpty() || kv[1].isEmpty()) {
                throw new IllegalArgumentException("Write items as product=quantity, not: " + parts[i]);
            }
            String product = kv[0].toLowerCase();
            if (fields.containsKey(product)) {
                throw new IllegalArgumentException("Product given twice: " + product);
            }
            // Send numbers as JSON numbers; anything else as a string so the server can refuse it.
            Object quantity;
            try {
                quantity = Long.parseLong(kv[1]);
            } catch (NumberFormatException e) {
                quantity = kv[1];
            }
            fields.put(product, quantity);
        }
        return Json.object(fields);
    }
}
