package json;

import java.util.List;
import java.util.Map;

public class JsonBuilder {
    public static String build(Object obj) {
        if (obj instanceof Map) {
            return buildFromMap((Map<?, ?>) obj);
        } else if (obj instanceof List) {
            return buildFromList((List<?>) obj);
        } else if (obj instanceof String) {
            return "\"" + escapeJson((String) obj) + "\"";
        } else if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        return "null";
    }

    private static String buildFromMap(Map<?, ?> map) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                builder.append(",");
            }
            builder.append("\"").append(entry.getKey()).append("\":")
                    .append(build(entry.getValue()));
            first = false;
        }

        builder.append("}");
        return builder.toString();
    }

    private static String buildFromList(List<?> list) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;

        for (Object item : list) {
            if (!first) {
                builder.append(",");
            }
            builder.append(build(item));
            first = false;
        }

        builder.append("]");
        return builder.toString();
    }

    private static String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}