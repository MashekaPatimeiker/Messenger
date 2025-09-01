package json;

import json.JsonBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonXmlExample {
    public static String getJsonResponse() {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("status", "success");
            data.put("timestamp", System.currentTimeMillis());

            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", 123);
            user.put("name", "Иван Иванов");
            user.put("email", "ivan@example.com");
            user.put("active", true);
            user.put("roles", List.of("user", "admin"));
            user.put("preferences", Map.of(
                    "theme", "dark",
                    "notifications", true,
                    "language", "ru"
            ));

            data.put("data", user);
            data.put("metadata", Map.of(
                    "version", "1.0.0",
                    "api_docs", "/api/docs"
            ));

            return JsonBuilder.build(data);
        } catch (Exception e) {
            e.printStackTrace();
            return getErrorResponse("Failed to generate JSON", 500);
        }
    }
    public static String getAuthDisabledResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "error");
        response.put("code", 403);
        response.put("message", "Authentication is currently disabled");
        response.put("timestamp", System.currentTimeMillis());
        return JsonBuilder.build(response);
    }
    public static String toJson(Object object) {
        try {
            return JsonBuilder.build(object);
        } catch (Exception e) {
            e.printStackTrace();
            return getErrorResponse("Failed to convert object to JSON", 500);
        }
    }
    public static Map<String, Object> parseJson(String json) {
        try {
            // Простая реализация парсинга JSON (заглушка)
            // В реальном проекте здесь нужно использовать JSON парсер
            // Например, Jackson, Gson или другой библиотеки

            // Эта реализация предполагает, что у вас есть JsonParser класс
            // Если его нет, вам нужно добавить зависимость или реализовать парсер

            return JsonParser.parse(json);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to parse JSON");
            error.put("original", json);
            return error;
        }
    }
    public static String getErrorResponse(String message, int code) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "error");
        response.put("code", code);
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        return JsonBuilder.build(response);
    }
}