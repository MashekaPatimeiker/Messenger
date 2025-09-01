package json;

import java.util.*;
import java.util.regex.*;

public class JsonParser {
    private static final Pattern OBJECT_PATTERN = Pattern.compile(
            "\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"\\s*:\\s*" +
                    "(\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"|" +
                    "true|false|null|" +
                    "-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?|" +
                    "\\{[^{}]*\\}|" +
                    "\\[[^\\]]*\\])" +
                    "\\s*(?:,|$)"
    );

    public static Map<String, Object> parse(String json) throws JsonParseException {
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new JsonParseException("JSON must start with { and end with }");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        String content = json.substring(1, json.length() - 1).trim();

        if (content.isEmpty()) {
            return result;
        }

        Matcher matcher = OBJECT_PATTERN.matcher(content);
        int lastPos = 0;

        while (matcher.find()) {
            if (matcher.start() != lastPos) {
                throw new JsonParseException("Unexpected characters at position " + lastPos);
            }
            lastPos = matcher.end();

            String key = matcher.group(1);
            String value = matcher.group(2).trim();
            result.put(key, parseValue(value));
        }

        if (lastPos != content.length()) {
            throw new JsonParseException("Unexpected trailing characters");
        }

        return result;
    }

    private static Object parseValue(String value) throws JsonParseException {
        if (value.startsWith("\"")) {
            return parseString(value);
        } else if (value.startsWith("{")) {
            return parse(value);
        } else if (value.startsWith("[")) {
            return parseArray(value);
        } else if (value.equals("true") || value.equals("false")) {
            return Boolean.parseBoolean(value);
        } else if (value.equals("null")) {
            return null;
        } else if (value.matches("-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")) {
            return parseNumber(value);
        }
        throw new JsonParseException("Invalid value: " + value);
    }

    private static String parseString(String value) throws JsonParseException {
        if (!value.endsWith("\"")) {
            throw new JsonParseException("Unterminated string");
        }
        return value.substring(1, value.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\/", "/")
                .replace("\\b", "\b")
                .replace("\\f", "\f")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\u", "\\\\u");
    }

    private static Number parseNumber(String value) {
        if (value.contains(".") || value.contains("e") || value.contains("E")) {
            return Double.parseDouble(value);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return Long.parseLong(value);
        }
    }

    private static List<Object> parseArray(String array) throws JsonParseException {
        array = array.trim();
        if (!array.startsWith("[") || !array.endsWith("]")) {
            throw new JsonParseException("Array must start with [ and end with ]");
        }

        List<Object> result = new ArrayList<>();
        String content = array.substring(1, array.length() - 1).trim();

        if (content.isEmpty()) {
            return result;
        }

        // Split elements carefully
        int depth = 0;
        boolean inString = false;
        int start = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"' && (i == 0 || content.charAt(i-1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '{' || c == '[') depth++;
                if (c == '}' || c == ']') depth--;
                if (c == ',' && depth == 0) {
                    result.add(parseValue(content.substring(start, i).trim()));
                    start = i + 1;
                }
            }
        }

        if (start < content.length()) {
            result.add(parseValue(content.substring(start).trim()));
        }

        return result;
    }

    public static class JsonParseException extends Exception {
        public JsonParseException(String message) {
            super(message);
        }
    }
}