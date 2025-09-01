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
import security.SimpleTokenUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;

public class Main {
    private static boolean authEnabled;
    static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    static final String DB_USER = "postgres";
    static final String DB_PASSWORD = "postgress";
    private static WebSocketServer webSocketServer;
    public static void main(String[] args) {
        initializeConfiguration();
        initializeDatabaseConnection();

        // Запускаем WebSocket сервер
        startWebSocketServer();
        System.out.println("WebSocket server status: " +
                (webSocketServer != null ? "RUNNING" : "NOT RUNNING"));

        if (webSocketServer != null) {
            System.out.println("WebSocket server should be listening on:");
            System.out.println("ws://localhost:8081/ws");
            System.out.println("ws://127.0.0.1:8081/ws");
            System.out.println("ws://192.168.100.13:8081/ws");
        }

        startTokenCleanupTask();
        Router router = setupRouter();
        setupLogoutRoute(router);
        setupUserInfoRoute(router);
        setupCheckAuthRoute(router);
        setupStaticFiles(router);
        setupApiRoutes(router);
        startServer(router);
    }
    public static WebSocketServer getWebSocketServer() {
        return webSocketServer;
    }
    private static void startTokenCleanupTask() {
        // Очищаем просроченные токены каждые 5 минут
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                SimpleTokenUtils.cleanupExpiredTokens();
                System.out.println("Cleaned up expired tokens");
            }
        }, 300000, 300000); // 5 минут
    }

    // Добавьте этот метод
    private static void setupUserInfoRoute(Router router) {
        router.get("/api/user-info", (req, res) -> {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String token = getAuthTokenFromRequest(req);
                int userId = getUserIdFromToken(conn, token);

                if (userId == -1) {
                    res.setStatusCode(401);
                    return JsonXmlExample.getErrorResponse("Unauthorized", 401);
                }

                // Получаем информацию о пользователе
                String sql = "SELECT user_id, user_name FROM users WHERE user_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, userId);
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        Map<String, Object> userInfo = new HashMap<>();
                        userInfo.put("id", rs.getInt("user_id"));
                        userInfo.put("username", rs.getString("user_name"));

                        res.addHeader("Content-Type", "application/json");
                        return JsonBuilder.build(Map.of("user", userInfo));
                    }
                }

                res.setStatusCode(404);
                return JsonXmlExample.getErrorResponse("User not found", 404);

            } catch (Exception e) {
                res.setStatusCode(500);
                return JsonXmlExample.getErrorResponse("Server error", 500);
            }
        });
    }
    private static void startWebSocketServer() {
        int webSocketPort = 8081; // Явно указываем порт
        WebSocketMessageProcessor processor = new WebSocketMessageProcessor(webSocketServer);
        webSocketServer = new WebSocketServer(webSocketPort);

        new Thread(() -> {
            webSocketServer.start();
            System.out.println("WebSocket server started on port " + webSocketPort);
        }).start();
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
        setupChatRoutes(router);
        setupGetMessagesRoute(router);
        setupGetChatsRoute(router);
        setupSendMessageRoute(router);
        setupRefreshTokenRoute(router);      // Add this
        setupUserInfoRoute(router);
        setupValidateTokenRoute(router);
    }
    private static void setupValidateTokenRoute(Router router) {
        router.post("/api/validate-token", (req, res) -> {
            try {
                Map<String, Object> requestData = JsonParser.parse(req.getBody());
                String token = (String) requestData.get("token");

                if (token == null || token.isEmpty()) {
                    res.setStatusCode(400);
                    return JsonXmlExample.getErrorResponse("Token required", 400);
                }

                // Валидируем токен
                SimpleTokenUtils.TokenData tokenData = SimpleTokenUtils.validateToken(token);

                if (tokenData != null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "valid");
                    response.put("user_id", tokenData.getUserId());
                    response.put("username", tokenData.getUsername());
                    response.put("expires_in", tokenData.getExpirationTime() - System.currentTimeMillis());

                    res.addHeader("Content-Type", "application/json");
                    return JsonBuilder.build(response);
                } else {
                    res.setStatusCode(401);
                    return JsonXmlExample.getErrorResponse("Invalid token", 401);
                }

            } catch (Exception e) {
                System.err.println("Validate token error: " + e.getMessage());
                res.setStatusCode(500);
                return JsonXmlExample.getErrorResponse("Server error", 500);
            }
        });
    }
    private static void setupChatRoutes(Router router) {
        // Этот метод теперь пустой, так как все маршруты вынесены в отдельные методы
    }

    private static void setupGetMessagesRoute(Router router) {
        router.get("/api/messages", (req, res) -> {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String token = getAuthTokenFromRequest(req);
                int userId = getUserIdFromToken(conn, token);

                if (userId == -1) {
                    res.setStatusCode(401);
                    return JsonXmlExample.getErrorResponse("Unauthorized", 401);
                }

                Map<String, String> queryParams = getQueryParams(req);
                String chatId = queryParams.get("chat_id");
                if (chatId == null) {
                    res.setStatusCode(400);
                    return JsonXmlExample.getErrorResponse("chat_id parameter required", 400);
                }

                // Проверяем, что пользователь является участником чата
                String checkSql = "SELECT 1 FROM chat_members WHERE chat_id = ? AND user_id = ?";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setInt(1, Integer.parseInt(chatId));
                    checkStmt.setInt(2, userId);
                    if (!checkStmt.executeQuery().next()) {
                        res.setStatusCode(403);
                        return JsonXmlExample.getErrorResponse("Access denied", 403);
                    }
                }

                String sql = """
                SELECT m.message_id, m.message_text, m.sent_at, 
                       u.user_name as sender_name,
                       (u.user_id = ?) as is_own_message
                FROM messages m
                JOIN users u ON m.sender_id = u.user_id
                WHERE m.chat_id = ?
                ORDER BY m.sent_at ASC
                """;

                List<Map<String, Object>> messages = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, userId);
                    stmt.setInt(2, Integer.parseInt(chatId));
                    ResultSet rs = stmt.executeQuery();

                    while (rs.next()) {
                        Map<String, Object> message = new HashMap<>();
                        message.put("message_id", rs.getInt("message_id"));
                        message.put("text", rs.getString("message_text"));
                        message.put("time", rs.getTimestamp("sent_at"));
                        message.put("sender", rs.getString("sender_name"));
                        message.put("is_own", rs.getBoolean("is_own_message"));
                        messages.add(message);
                    }
                }

                res.addHeader("Content-Type", "application/json");
                return JsonBuilder.build(Map.of("messages", messages));

            } catch (Exception e) {
                res.setStatusCode(500);
                return JsonXmlExample.getErrorResponse("Server error", 500);
            }
        });
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

    private static void setupGetChatsRoute(Router router) {
        router.get("/api/chats", (req, res) -> {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String token = getAuthTokenFromRequest(req);
                int userId = getUserIdFromToken(conn, token);

                if (userId == -1) {
                    res.setStatusCode(401);
                    return JsonXmlExample.getErrorResponse("Unauthorized", 401);
                }

                String sql = """
                SELECT c.chat_id, c.chat_name, c.chat_type, 
                       (SELECT message_text FROM messages 
                        WHERE chat_id = c.chat_id 
                        ORDER BY sent_at DESC LIMIT 1) as last_message,
                       (SELECT sent_at FROM messages 
                        WHERE chat_id = c.chat_id 
                        ORDER BY sent_at DESC LIMIT 1) as last_message_time
                FROM chats c
                JOIN chat_members cm ON c.chat_id = cm.chat_id
                WHERE cm.user_id = ?
                ORDER BY last_message_time DESC NULLS LAST
                """;

                List<Map<String, Object>> chats = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, userId);
                    ResultSet rs = stmt.executeQuery();

                    while (rs.next()) {
                        Map<String, Object> chat = new HashMap<>();
                        chat.put("chat_id", rs.getInt("chat_id"));
                        chat.put("chat_name", rs.getString("chat_name"));
                        chat.put("chat_type", rs.getString("chat_type"));
                        chat.put("last_message", rs.getString("last_message"));
                        chat.put("last_message_time", rs.getTimestamp("last_message_time"));
                        chats.add(chat);
                    }
                }

                res.addHeader("Content-Type", "application/json");
                return JsonBuilder.build(Map.of("chats", chats));

            } catch (Exception e) {
                res.setStatusCode(500);
                return JsonXmlExample.getErrorResponse("Server error", 500);
            }
        });
    }

    private static void registerUser(Connection conn, String username, String password) throws SQLException {
        String insertSql = "INSERT INTO users (user_name, user_password) VALUES (?, ?)";
        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            insertStmt.setString(1, username);
            insertStmt.setString(2, password);
            insertStmt.executeUpdate();
        }
    }
    private static void setupRefreshTokenRoute(Router router) {
        router.post("/api/refresh-token", (req, res) -> {
            try {
                String token = null;
                String cookieHeader = req.getHeader("Cookie");

                if (cookieHeader != null) {
                    for (String cookie : cookieHeader.split(";")) {
                        if (cookie.trim().startsWith("auth_token=")) {
                            token = cookie.trim().substring("auth_token=".length());
                            break;
                        }
                    }
                }

                if (token == null) {
                    String authHeader = req.getHeader("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        token = authHeader.substring(7);
                    }
                }

                if (token == null || token.isEmpty()) {
                    res.setStatusCode(401);
                    return JsonXmlExample.getErrorResponse("Token required", 401);
                }

                try {
                    Map<String, Object> claims = SimpleTokenUtils.verifyToken(token);
                    int userId = (Integer) claims.get("user_id");
                    String username = (String) claims.get("username");

                    String newToken = SimpleTokenUtils.generateToken(userId, username);

                    // Используем те же настройки cookie
                    String cookieSettings = "auth_token=" + newToken +
                            "; Path=/" +
                            "; HttpOnly" +
                            "; SameSite=Lax";

                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "success");
                    response.put("token", newToken);

                    res.addHeader("Content-Type", "application/json");
                    res.addHeader("Set-Cookie", cookieSettings);

                    return JsonBuilder.build(response);

                } catch (Exception e) {
                    res.setStatusCode(401);
                    return JsonXmlExample.getErrorResponse("Invalid token", 401);
                }

            } catch (Exception e) {
                System.err.println("Refresh token error: " + e.getMessage());
                res.setStatusCode(500);
                return JsonXmlExample.getErrorResponse("Server error", 500);
            }
        });
    }
    private static void setupLoginRoute(Router router) {
        router.post("/api/login", (req, res) -> {
            try {
                Map<String, Object> requestData = JsonParser.parse(req.getBody());
                String username = ((String) requestData.get("username")).trim();
                String password = ((String) requestData.get("password")).trim();

                System.out.println("[DEBUG] Login attempt: username='" + username + "'");

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    String sql = "SELECT user_id, user_password FROM users WHERE user_name = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, username);
                        ResultSet rs = stmt.executeQuery();

                        if (rs.next()) {
                            String storedPassword = rs.getString("user_password");
                            int userId = rs.getInt("user_id");

                            if (password.equals(storedPassword)) {
                                String token = SimpleTokenUtils.generateToken(userId, username);

                                // Динамически определяем настройки cookie
                                String host = req.getHeader("Host");
// НА ЭТО:
                                boolean isLocalhost = host.contains("localhost") || host.contains("127.0.0.1") || host.contains("192.168.");
                                String cookieSettings = "auth_token=" + token +
                                        "; Path=/" +
                                        "; HttpOnly" +
                                        "; SameSite=" + (isLocalhost ? "Lax" : "None") +
                                        (isLocalhost ? "" : "; Secure");
                                System.out.println("[DEBUG LOGIN] Setting cookie: " + cookieSettings);
                                System.out.println("[DEBUG LOGIN] Host header: " + host);
                                System.out.println("[DEBUG LOGIN] User-Agent: " + req.getHeader("User-Agent"));

                                Map<String, Object> response = new HashMap<>();
                                response.put("status", "success");
                                response.put("token", token);
                                response.put("user", Map.of(
                                        "id", userId,
                                        "username", username
                                ));

                                res.addHeader("Content-Type", "application/json");
                                res.addHeader("Set-Cookie", cookieSettings);

                                System.out.println("[DEBUG LOGIN] Response headers: " + res.getHeaders());
                                return JsonBuilder.build(response);
                            }
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
    }
    private static void setupCheckAuthRoute(Router router) {
        router.get("/api/check-auth", (req, res) -> {
            String token = getAuthTokenFromRequest(req);
            System.out.println("[DEBUG CHECK-AUTH] Token: " + (token.isEmpty() ? "EMPTY" : "PRESENT"));

            SimpleTokenUtils.TokenData tokenData = SimpleTokenUtils.validateToken(token);
            System.out.println("[DEBUG CHECK-AUTH] Token valid: " + (tokenData != null));

            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", tokenData != null);
            if (tokenData != null) {
                response.put("user", Map.of(
                        "id", tokenData.getUserId(),
                        "username", tokenData.getUsername()
                ));
            }

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
        // Добавьте проверку токена
        String token = getAuthTokenFromRequest(req);
        System.out.println("[DEBUG CHAT] Token in chat request: " + (token.isEmpty() ? "EMPTY" : "PRESENT"));

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

    private static void setupSendMessageRoute(Router router) {
        router.post("/api/send-message", (req, res) -> {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String token = getAuthTokenFromRequest(req);
                int userId = getUserIdFromToken(conn, token);

                if (userId == -1) {
                    res.setStatusCode(401);
                    return JsonXmlExample.getErrorResponse("Unauthorized", 401);
                }

                Map<String, Object> requestData = JsonParser.parse(req.getBody());
                Integer chatId = (Integer) requestData.get("chat_id");
                String messageText = (String) requestData.get("text");

                if (chatId == null || messageText == null || messageText.trim().isEmpty()) {
                    res.setStatusCode(400);
                    return JsonXmlExample.getErrorResponse("Invalid parameters", 400);
                }

                // Проверяем доступ к чату
                String checkSql = "SELECT 1 FROM chat_members WHERE chat_id = ? AND user_id = ?";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setInt(1, chatId);
                    checkStmt.setInt(2, userId);
                    if (!checkStmt.executeQuery().next()) {
                        res.setStatusCode(403);
                        return JsonXmlExample.getErrorResponse("Access denied", 403);
                    }
                }

                // Сохраняем сообщение
                String insertSql = "INSERT INTO messages (chat_id, sender_id, message_text) VALUES (?, ?, ?)";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    insertStmt.setInt(1, chatId);
                    insertStmt.setInt(2, userId);
                    insertStmt.setString(3, messageText);
                    insertStmt.executeUpdate();

                    // Получаем ID вставленного сообщения
                    ResultSet rs = insertStmt.getGeneratedKeys();
                    if (rs.next()) {
                        int messageId = rs.getInt(1);

                        Map<String, Object> response = new HashMap<>();
                        response.put("status", "success");
                        response.put("message_id", messageId);
                        res.addHeader("Content-Type", "application/json");
                        return JsonBuilder.build(response);
                    }
                }

                res.setStatusCode(500);
                return JsonXmlExample.getErrorResponse("Failed to send message", 500);

            } catch (Exception e) {
                res.setStatusCode(500);
                return JsonXmlExample.getErrorResponse("Server error", 500);
            }
        });
    }

    private static int getUserIdFromToken(Connection conn, String token) throws SQLException {
        SimpleTokenUtils.TokenData tokenData = SimpleTokenUtils.validateToken(token);
        return tokenData != null ? tokenData.getUserId() : -1;
    }

    private static void setupProtectedRoute(Router router) {
        router.get("/api/protected", (req, res) -> {
            if (!authEnabled) {
                res.setStatusCode(403);
                res.addHeader("Content-Type", "application/json; charset=UTF-8");
                return getAuthDisabledResponse();
            }

            String authToken = getAuthTokenFromRequest(req);

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

    // Добавляем метод generateWebSocketAccept в Main
    static String generateWebSocketAccept(String key) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            String magicString = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
            byte[] hash = md.digest(magicString.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        System.out.println("[DEBUG] Full Cookie header: " + cookieHeader);
        System.out.println("[DEBUG] Request host: " + req.getHeader("Host"));
        System.out.println("[DEBUG] User-Agent: " + req.getHeader("User-Agent"));

        String token = Arrays.stream(cookieHeader.split(";"))
                .map(String::trim)
                .filter(c -> c.startsWith("auth_token="))
                .map(c -> c.substring("auth_token=".length()))
                .findFirst()
                .orElse("");

        System.out.println("[DEBUG] Extracted token: " + (token.isEmpty() ? "EMPTY" : "FOUND (" + token.length() + " chars)"));
        return token;
    }
    // Метод для извлечения query параметров из запроса
    private static Map<String, String> getQueryParams(HttpRequest req) {
        Map<String, String> queryParams = new HashMap<>();

        String queryString = req.getQueryString();
        if (queryString == null || queryString.isEmpty()) {
            return queryParams;
        }

        try {
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    queryParams.put(key, value);
                } else if (keyValue.length == 1) {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    queryParams.put(key, "");
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing query parameters: " + e.getMessage());
        }

        return queryParams;
    }
    private static void setupLogoutRoute(Router router) {
        router.post("/api/logout", (req, res) -> {
            String token = getAuthTokenFromRequest(req);

            // Используем те же настройки cookie для удаления
            String cookieSettings = "auth_token=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT" +
                    "; HttpOnly" +
                    "; SameSite=Lax";
            res.addHeader("Set-Cookie", cookieSettings);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            res.addHeader("Content-Type", "application/json");
            return JsonBuilder.build(response);
        });
    }
}