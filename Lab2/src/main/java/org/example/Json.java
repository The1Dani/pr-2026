package org.example;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Json {

    /** Builds one flat JSON object from key/value pairs, in insertion order. */
    public static String object(Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(quote(e.getKey())).append(':');
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) sb.append(v);   // 1, true
            else sb.append(quote(String.valueOf(v)));                          // "menu"
        }
        return sb.append('}').toString();
    }

    /** Shortcut for a message with only an action: {"action":"menu"}. */
    public static String action(String action) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("action", action);
        return object(m);
    }

    /** Puts a string in double quotes and escapes what JSON requires. */
    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    /** Reads one string field from a flat JSON line, or null if it is not there. */
    public static String getString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key)
                + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Reads every field whose value is a whole number from a flat JSON line,
     * in order: {"Coffee":27,"Tea":4} -> {Coffee=27, Tea=4}.
     */
    public static Map<String, Long> getInts(String json) {
        Pattern p = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*(-?\\d+)\\s*(?=[,}])");
        Matcher m = p.matcher(json);
        Map<String, Long> result = new LinkedHashMap<>();
        while (m.find()) {
            try {
                result.put(m.group(1), Long.parseLong(m.group(2)));
            } catch (NumberFormatException ignored) {      // too big for a long
            }
        }
        return result;
    }
}
