import http.httpdiff.HttpRequest;
import http.middleware.CharsetMiddleware;
import http.middleware.CorsMiddleware;
import http.middleware.LoggingMiddleware;
import http.sitediff.Router;
import http.sitediff.StaticFileHandler;
import json.JsonBuilder;
import json.JsonParser;
import json.JsonXmlExample;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Main {
    private static boolean authEnabled;
    private static final String SECRET_TOKEN = "sample-jwt-token";
    public static void main(String[] args) {
        try {
            config.Config.load("config/server.conf");
        } catch (IOException e) {
            System.out.println("Using default configuration");
        }
        authEnabled = config.Config.getBoolean("api.authEnabled");
        System.out.println("Authentication is " + (authEnabled ? "ENABLED" : "DISABLED"));

        Router router = new Router();
        router.use(new LoggingMiddleware());
        router.use(new CharsetMiddleware());

        if (config.Config.getBoolean("api.enableCors")) {
            router.use(new CorsMiddleware());
        }

        String staticFilesPath = config.Config.get("server.staticFiles");
        Path staticPath = Paths.get(staticFilesPath).toAbsolutePath();
        System.out.println("Static files path: " + staticPath);

        createDefaultFilesIfNotExist(staticPath);
        StaticFileHandler staticHandler = new StaticFileHandler(staticFilesPath);
        router.setStaticFileHandler(staticHandler);

        router.post("/api/echo", (req, res) -> {
            try {
                Map<String, Object> requestData = JsonParser.parse(req.getBody());
                String name = (String) requestData.getOrDefault("name", "Друг");
                String responseMessage = String.format("Привет, %s! Твой запрос успешно получен.", name);
                res.addHeader("Content-Type", "text/plain; charset=UTF-8");
                return responseMessage;
            } catch (Exception e) {
                res.setStatusCode(400);
                res.addHeader("Content-Type", "text/plain; charset=UTF-8");
                return "Ошибка: Неверный формат запроса";
            }
        });
        router.get("/chat", (req, res) -> {
            String authToken = getAuthTokenFromRequest(req);

            if (authEnabled && !"sample-jwt-token".equals(authToken)) {
                res.setStatusCode(302);
                res.addHeader("Location", "/login");
                return "";
            }

            res.addHeader("Content-Type", "text/html; charset=UTF-8");
            try {
                Path indexPath = staticPath.resolve("html/chat.html");
                return Files.readString(indexPath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                res.setStatusCode(500);
                return "Error: Unable to read chat.html";
            }
        });

        router.get("/login", (req, res) -> {
            res.addHeader("Content-Type", "text/html; charset=UTF-8");
            try {
                Path loginPath = staticPath.resolve("html/login.html");
                return Files.readString(loginPath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                res.setStatusCode(500);
                return "Error: Unable to read login page";
            }
        });

        router.get("/", (req, res) -> {
            res.setStatusCode(302);
            res.addHeader("Location", "/login");
            return "";
        });
        router.get("/api/json", (req, res) -> {
            res.addHeader("Content-Type", "application/json; charset=UTF-8");
            try {
                String json = JsonXmlExample.getJsonResponse();
                res.addHeader("Content-Length", String.valueOf(json.getBytes(StandardCharsets.UTF_8).length));
                return json;
            } catch (Exception e) {
                res.setStatusCode(500);
                return JsonXmlExample.getErrorResponse("Internal server error", 500);
            }
        });

        router.post("/api/login", (req, res) -> {
            try {
                if (!authEnabled) {
                    res.setStatusCode(403);
                    res.addHeader("Content-Type", "application/json");
                    return getAuthDisabledResponse();
                }

                // Логируем тело запроса для отладки
                String body = req.getBody();
                System.out.println("Raw login request: " + body);

                Map<String, Object> requestData = JsonParser.parse(body);
                System.out.println("Parsed login data: " + requestData);

                String username = (String) requestData.get("username");
                String password = (String) requestData.get("password");

                System.out.println("Auth attempt: " + username + "/" + password);

                Map<String, Object> response = new HashMap<>();
                if ("admin".equals(username) && "123321".equals(password)) {
                    response.put("status", "success");
                    response.put("token", SECRET_TOKEN);

                    // Устанавливаем cookie с токеном
                    String cookie = String.format("auth_token=%s; Path=/; HttpOnly; SameSite=Strict", SECRET_TOKEN);
                    res.addHeader("Set-Cookie", cookie);

                    System.out.println("Login successful for user: " + username);
                } else {
                    response.put("status", "error");
                    response.put("message", "Invalid credentials");
                    res.setStatusCode(401);
                    System.out.println("Login failed for user: " + username);
                }

                res.addHeader("Content-Type", "application/json");
                return JsonBuilder.build(response);
            } catch (Exception e) {
                System.err.println("Login error: " + e.getMessage());
                e.printStackTrace();
                res.setStatusCode(500);
                return JsonXmlExample.getErrorResponse("Login processing error", 500);
            }
        });

        // Добавим endpoint для проверки авторизации
        router.get("/api/check-auth", (req, res) -> {
            String token = getAuthTokenFromRequest(req);
            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", SECRET_TOKEN.equals(token));
            res.addHeader("Content-Type", "application/json");
            return JsonBuilder.build(response);
        });

        router.get("/api/protected", (req, res) -> {
            if (!authEnabled) {
                res.setStatusCode(403);
                res.addHeader("Content-Type", "application/json; charset=UTF-8");
                return getAuthDisabledResponse();
            }

            String authToken = getAuthTokenFromRequest(req);
            if (!"sample-jwt-token".equals(authToken)) {
                res.setStatusCode(401);
                res.addHeader("Content-Type", "application/json; charset=UTF-8");
                return JsonXmlExample.getErrorResponse("Unauthorized", 401);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Welcome to protected area!");
            res.addHeader("Content-Type", "application/json; charset=UTF-8");
            return JsonBuilder.build(response);
        });

        new Server(router).initserver();
    }

    private static String getAuthDisabledResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("code", 403);
        response.put("message", "Authentication is currently disabled");
        response.put("timestamp", System.currentTimeMillis());
        return JsonBuilder.build(response);
    }

    private static void createDefaultFilesIfNotExist(Path staticPath) {
        try {
            Files.createDirectories(staticPath.resolve("html"));
            Files.createDirectories(staticPath.resolve("css"));
            Files.createDirectories(staticPath.resolve("js"));
            Files.createDirectories(staticPath.resolve("images"));
        } catch (IOException e) {
            System.err.println("Failed to create default files: " + e.getMessage());
        }
    }
    private static String getAuthTokenFromRequest(HttpRequest req) {
        String cookieHeader = req.getHeaders().getOrDefault("Cookie", "");
        return Arrays.stream(cookieHeader.split(";"))
                .map(String::trim)
                .filter(c -> c.startsWith("auth_token="))
                .map(c -> c.substring("auth_token=".length()))
                .findFirst()
                .orElse("");
    }
}