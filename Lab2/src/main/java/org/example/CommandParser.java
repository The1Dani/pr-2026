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

    public static final String HELP = """
            Commands:
              menu                              show products and stock
              order <product>=<qty> ...         e.g. order coffee=1 tea=2
              deliver <product>=<qty> ...       (provider) e.g. deliver coffee=20
              {...}                             send a raw JSON line as-is
              help                              show this help
              quit                              disconnect and exit""";

    /** Returns the JSON line to send. Throws IllegalArgumentException with a message for bad input. */
    public static String parse(String input) {
        String line = input.strip();
        if (line.startsWith("{")) {
            return line;
        }

        String[] parts = line.split("\\s+");
        String command = parts[0].toLowerCase();

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
