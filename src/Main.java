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
import java.sql.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Main {
    private static boolean authEnabled;
    private static final String SECRET_TOKEN = "sample-jwt-token";
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/user_db";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgress";
    public static void main(String[] args) {
        try {
            config.Config.load("config/server.conf");
        } catch (IOException e) {
            System.out.println("Using default configuration");
        }
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL JDBC Driver not found");
            e.printStackTrace();
            return;
        }
        authEnabled = config.Config.getBoolean("api.authEnabled");
        System.out.println("Authentication is " + (authEnabled ? "ENABLED" : "DISABLED"));

        Router router = new Router();
        router.use(new LoggingMiddleware());
        router.use(new CharsetMiddleware());

        if (config.Config.getBoolean("api.enableCors")) {
            router.use(new CorsMiddleware());
        }
        try (Connection testConn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            System.out.println("Подключение к PostgreSQL успешно!");
        } catch (SQLException e) {
            System.err.println("Ошибка подключения: " + e.getMessage());
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
        router.post("/api/register", (req, res) -> {
            try {
                Map<String, Object> requestData = JsonParser.parse(req.getBody());
                String username = ((String) requestData.get("username")).trim();
                String password = ((String) requestData.get("password")).trim();

                // Валидация
                if (username.length() < 3) {
                    res.setStatusCode(400);
                    return JsonXmlExample.getErrorResponse("Имя должно быть от 3 символов", 400);
                }

                if (password.length() < 6) {
                    res.setStatusCode(400);
                    return JsonXmlExample.getErrorResponse("Пароль должен быть от 6 символов", 400);
                }

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    // Проверка существования пользователя
                    String checkSql = "SELECT user_name FROM users WHERE user_name = ?";
                    try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                        checkStmt.setString(1, username);
                        try (ResultSet rs = checkStmt.executeQuery()) {
                            if (rs.next()) {
                                res.setStatusCode(400);
                                return JsonXmlExample.getErrorResponse("Имя уже занято", 400);
                            }
                        }
                    }

                    // Сохраняем пароль как текст (в реальном приложении используйте BCrypt)
                    String insertSql = "INSERT INTO users (user_name, user_password) VALUES (?, ?)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setString(1, username);
                        insertStmt.setString(2, password);  // Теперь передаем строку
                        insertStmt.executeUpdate();
                    }

                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "success");
                    response.put("message", "Регистрация успешна");
                    res.addHeader("Content-Type", "application/json");
                    return JsonBuilder.build(response);
                }
            } catch (SQLException e) {
                System.err.println("Ошибка регистрации: " + e.getMessage());
                res.setStatusCode(500);
                return JsonXmlExample.getErrorResponse("Ошибка базы данных", 500);
            } catch (Exception e) {
                System.err.println("Ошибка регистрации: " + e.getMessage());
                res.setStatusCode(400);
                return JsonXmlExample.getErrorResponse("Неверные данные запроса", 400);
            }
        });
        router.post("/api/login", (req, res) -> {
            try {
                Map<String, Object> requestData = JsonParser.parse(req.getBody());
                String username = ((String) requestData.get("username")).trim();
                String password = ((String) requestData.get("password")).trim();

                System.out.println("[DEBUG] Login attempt: username='" + username + "', password='" + password + "'");

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    String sql = "SELECT user_password FROM users WHERE user_name = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, username);
                        ResultSet rs = stmt.executeQuery();

                        if (rs.next()) {
                            String storedPassword = rs.getString("user_password");
                            System.out.println("[DEBUG] Stored password: " + storedPassword + ", input password: " + password);

                            if (password.equals(storedPassword)) {
                                Map<String, Object> response = new HashMap<>();
                                response.put("status", "success");
                                response.put("token", SECRET_TOKEN);
                                res.addHeader("Content-Type", "application/json");
                                res.addHeader("Set-Cookie", "auth_token=" + SECRET_TOKEN + "; Path=/; HttpOnly");
                                return JsonBuilder.build(response); // Возвращаем JSON, а не редирект
                            } else {
                                System.out.println("[DEBUG] Пароли не совпадают!");
                            }
                        } else {
                            System.out.println("[DEBUG] Пользователь '" + username + "' не найден!");
                        }
                    }
                }

                res.setStatusCode(401);
                return JsonXmlExample.getErrorResponse("Неверные данные", 401);

            } catch (Exception e) {
                System.err.println("Ошибка входа: " + e.getMessage());
                e.printStackTrace();
                res.setStatusCode(500);
                return JsonXmlExample.getErrorResponse("Ошибка сервера", 500);
            }
        });
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