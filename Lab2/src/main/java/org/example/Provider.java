package org.example;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The provider's automatic part. When stock falls below 5, the server sends the
 * provider a message with no "action", only how much of each product to bring
 * so stock gets back to 30, e.g. {"Coffee":27,"Tea":4}.
 * This class answers it right away with {"action":"delivery","coffee":27,"tea":4}.
 * The server does not reply to deliveries, so we print a local confirmation.
 */
public class Provider {
    private final Connection connection;

    public Provider(Connection connection) {
        this.connection = connection;
    }

    /** Called by the MessageReader thread for every line from the server. */
    public void onServerMessage(String line) {
        if (Json.getString(line, "Error") != null) return;   // errors are only shown

        Map<String, Long> requested = Json.getInts(line);
        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("action", "delivery");
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<String, Long> e : requested.entrySet()) {
            if (e.getValue() <= 0) continue;
            String product = e.getKey().toLowerCase();
            delivery.put(product, e.getValue());
            summary.append(' ').append(product).append('=').append(e.getValue());
        }
        if (delivery.size() == 1) return;                    // nothing to deliver

        try {
            connection.send(Json.object(delivery));          // send() is synchronized, safe from this thread
            Console.event("[provider] restock requested, delivered:" + summary);
        } catch (IOException ex) {
            Console.event("[provider] could not deliver: " + ex.getMessage());
        }
    }
}
