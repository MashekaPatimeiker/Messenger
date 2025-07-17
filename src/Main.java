import http.httpdiff.HttpRequest;
import http.httpdiff.HttpResponse;
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
        initializeConfiguration();
        initializeDatabaseConnection();
        Router router = setupRouter();
        setupStaticFiles(router);
        setupApiRoutes(router);
        startServer(router);
    }

    private static void initializeConfiguration() {
        try {
            config.Config.load("config/server.conf");
        } catch (IOException e) {
            System.out.println("Using default configuration");
        }
        authEnabled = config.Config.getBoolean("api.authEnabled");
        System.out.println("Authentication is " + (authEnabled ? "ENABLED" : "DISABLED"));
    }

    private static void initializeDatabaseConnection() {
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection testConn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                System.out.println("Подключение к PostgreSQL успешно!");
            }
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL JDBC Driver not found");
            e.printStackTrace();
            System.exit(1);
        } catch (SQLException e) {
            System.err.println("Ошибка подключения: " + e.getMessage());
        }
    }

    private static Router setupRouter() {
        Router router = new Router();
        router.use(new LoggingMiddleware());
        router.use(new CharsetMiddleware());

        if (config.Config.getBoolean("api.enableCors")) {
            router.use(new CorsMiddleware());
        }
        return router;
    }

    private static void setupStaticFiles(Router router) {
        String staticFilesPath = config.Config.get("server.staticFiles");
        Path staticPath = Paths.get(staticFilesPath).toAbsolutePath();
        System.out.println("Static files path: " + staticPath);

        createDefaultFilesIfNotExist(staticPath);
        StaticFileHandler staticHandler = new StaticFileHandler(staticFilesPath);
        router.setStaticFileHandler(staticHandler);
    }

    private static void setupApiRoutes(Router router) {
        setupEchoRoute(router);
        setupAuthRoutes(router);
        setupStaticPageRoutes(router);
        setupJsonApiRoute(router);
        setupProtectedRoute(router);
    }

    private static void setupEchoRoute(Router router) {
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
    }

    private static void setupAuthRoutes(Router router) {
        setupRegistrationRoute(router);
        setupLoginRoute(router);
        setupCheckAuthRoute(router);
    }

    private static void setupRegistrationRoute(Router router) {
        router.post("/api/register", (req, res) -> {
            try {
                Map<String, Object> requestData = JsonParser.parse(req.getBody());
                String username = ((String) requestData.get("username")).trim();
                String password = ((String) requestData.get("password")).trim();

                if (username.length() < 3) {
                    res.setStatusCode(400);
                    return JsonXmlExample.getErrorResponse("Имя должно быть от 3 символов", 400);
                }

                if (password.length() < 6) {
                    res.setStatusCode(400);
                    return JsonXmlExample.getErrorResponse("Пароль должен быть от 6 символов", 400);
                }

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    if (isUserExists(conn, username)) {
                        res.setStatusCode(400);
                        return JsonXmlExample.getErrorResponse("Имя уже занято", 400);
                    }

                    registerUser(conn, username, password);

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
    }

    private static boolean isUserExists(Connection conn, String username) throws SQLException {
        String checkSql = "SELECT user_name FROM users WHERE user_name = ?";
        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, username);
            try (ResultSet rs = checkStmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void registerUser(Connection conn, String username, String password) throws SQLException {
        String insertSql = "INSERT INTO users (user_name, user_password) VALUES (?, ?)";
        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            insertStmt.setString(1, username);
            insertStmt.setString(2, password);
            insertStmt.executeUpdate();
        }
    }

    private static void setupLoginRoute(Router router) {
        router.post("/api/login", (req, res) -> {
            try {
                Map<String, Object> requestData = JsonParser.parse(req.getBody());
                String username = ((String) requestData.get("username")).trim();
                String password = ((String) requestData.get("password")).trim();

                System.out.println("[DEBUG] Login attempt: username='" + username + "', password='" + password + "'");

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    String storedPassword = getStoredPassword(conn, username);

                    if (storedPassword != null && password.equals(storedPassword)) {
                        return createSuccessfulLoginResponse(res);
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
    }

    private static String getStoredPassword(Connection conn, String username) throws SQLException {
        String sql = "SELECT user_password FROM users WHERE user_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("user_password") : null;
        }
    }

    private static String createSuccessfulLoginResponse(HttpResponse res) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("token", SECRET_TOKEN);
        res.addHeader("Content-Type", "application/json");
        res.addHeader("Set-Cookie", "auth_token=" + SECRET_TOKEN + "; Path=/; HttpOnly");
        return JsonBuilder.build(response);
    }

    private static void setupCheckAuthRoute(Router router) {
        router.get("/api/check-auth", (req, res) -> {
            String token = getAuthTokenFromRequest(req);
            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", SECRET_TOKEN.equals(token));
            res.addHeader("Content-Type", "application/json");
            return JsonBuilder.build(response);
        });
    }

    private static void setupStaticPageRoutes(Router router) {
        router.get("/chat", (req, res) -> handleChatPageRequest(req, res));
        router.get("/login", (req, res) -> handleLoginPageRequest(res));
        router.get("/", (req, res) -> {
            res.setStatusCode(302);
            res.addHeader("Location", "/login");
            return "";
        });
    }

    private static String handleChatPageRequest(HttpRequest req, HttpResponse res) {
        String authToken = getAuthTokenFromRequest(req);

        if (authEnabled && !SECRET_TOKEN.equals(authToken)) {
            res.setStatusCode(302);
            res.addHeader("Location", "/login");
            return "";
        }

        res.addHeader("Content-Type", "text/html; charset=UTF-8");
        try {
            Path indexPath = Paths.get(config.Config.get("server.staticFiles")).resolve("html/chat.html");
            return Files.readString(indexPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            res.setStatusCode(500);
            return "Error: Unable to read chat.html";
        }
    }

    private static String handleLoginPageRequest(HttpResponse res) {
        res.addHeader("Content-Type", "text/html; charset=UTF-8");
        try {
            Path loginPath = Paths.get(config.Config.get("server.staticFiles")).resolve("html/login.html");
            return Files.readString(loginPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            res.setStatusCode(500);
            return "Error: Unable to read login page";
        }
    }

    private static void setupJsonApiRoute(Router router) {
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
    }

    private static void setupProtectedRoute(Router router) {
        router.get("/api/protected", (req, res) -> {
            if (!authEnabled) {
                res.setStatusCode(403);
                res.addHeader("Content-Type", "application/json; charset=UTF-8");
                return getAuthDisabledResponse();
            }

            String authToken = getAuthTokenFromRequest(req);
            if (!SECRET_TOKEN.equals(authToken)) {
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
    }

    private static void startServer(Router router) {
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