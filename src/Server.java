import http.httpdiff.HttpHandler;
import http.httpdiff.HttpHeader;
import http.httpdiff.HttpRequest;
import http.httpdiff.HttpResponse;
import http.sitediff.ContentType;
import json.JsonBuilder;
import json.JsonXmlExample;
import security.SimpleTokenUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.net.Socket;
import java.net.HttpCookie;
import java.util.concurrent.Future;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.sql.*;

public class Server {
    private final static int BUFFER_SIZE = 8192;
    private final static int MAX_REQUEST_SIZE = 65536;

    private final HttpHandler handler;
    private final String host;
    private final int port;

    // Данные БД
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgress";

    public Server(HttpHandler handler) {
        this(handler, config.Config.get("server.host"), config.Config.getInt("server.port"));
    }

    Server(HttpHandler handler, String host, int port) {
        this.handler = handler;
        this.host = host;
        this.port = port;
    }

    public void initserver() {
        try (AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(host, port));
            System.out.printf("Server started on %s:%d%n", host, port);

            while (true) {
                try {
                    Future<AsynchronousSocketChannel> future = server.accept();
                    handleClient(future);
                } catch (Exception e) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Server failed to start: " + e.getMessage());
        }
    }

    private void handleClient(Future<AsynchronousSocketChannel> future) {
        AsynchronousSocketChannel asyncChannel = null;
        String requestData = null;
        boolean isWebSocket = false;

        try {
            asyncChannel = future.get();
            System.out.println("New client connection");

            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            StringBuilder builder = new StringBuilder();
            int totalBytesRead = 0;

            while (true) {
                int bytesRead = asyncChannel.read(buffer).get();
                if (bytesRead == -1) break;

                totalBytesRead += bytesRead;
                if (totalBytesRead > MAX_REQUEST_SIZE) {
                    sendErrorResponse(asyncChannel, 413, "Request too large");
                    return;
                }

                buffer.flip();
                builder.append(StandardCharsets.UTF_8.decode(buffer));
                buffer.clear();

                if (builder.toString().contains("\r\n\r\n")) {
                    break;
                }
            }

            requestData = builder.toString();
            if (requestData.isEmpty()) {
                sendErrorResponse(asyncChannel, 400, "Empty request");
                return;
            }

            if (isWebSocketRequest(requestData)) {
                isWebSocket = true;
                System.out.println("WebSocket connection detected");

                // Перенаправляем в WebSocket сервер
                Main.getWebSocketServer().handleWebSocketConnection(asyncChannel, requestData);
                return; // Не закрываем соединение!
            }

            // Обычная HTTP обработка
            HttpRequest request = new HttpRequest(requestData);
            HttpResponse response = new HttpResponse();

            if (handler != null) {
                handleRequest(request, response);
            }

            sendResponse(asyncChannel, response);

        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            // Закрываем соединение только для HTTP, не для WebSocket
            if (asyncChannel != null && !isWebSocket) {
                try {
                    asyncChannel.close();
                } catch (IOException e) {
                    System.err.println("Error closing client channel: " + e.getMessage());
                }
            }
        }
    }

    private boolean isWebSocketRequest(String requestData) {
        return requestData.contains("Upgrade: websocket") &&
                requestData.contains("Connection: Upgrade") &&
                requestData.contains("Sec-WebSocket-Key");
    }

    private void handleRequest(HttpRequest request, HttpResponse response) {
        try {
            String path = request.getPath();

            // Обработка новых API endpoints
            if ("POST".equals(request.getMethod())) {
                if ("/api/validate-token".equals(path)) {
                    handleValidateToken(request, response);
                    return;
                } else if ("/api/refresh-token".equals(path)) {
                    handleRefreshToken(request, response);
                    return;
                }
            }

            // Стандартная обработка через handler
            String body = handler.handle(request, response);

            if (body != null && !body.isBlank()) {
                if (!response.getHeaders().containsKey(HttpHeader.CONTENT_TYPE)) {
                    response.addHeader(HttpHeader.CONTENT_TYPE, ContentType.APPLICATION_JSON_UTF8);
                }
                response.setBody(body);
            }

            response.addHeader("Connection", "close");
            byte[] bodyBytes = response.getBody().getBytes(StandardCharsets.UTF_8);
            response.addHeader("Content-Length", String.valueOf(bodyBytes.length));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatusCode(500);
            response.setStatus("Internal server error");
            response.addHeader(HttpHeader.CONTENT_TYPE, ContentType.APPLICATION_JSON_UTF8);
            response.setBody(JsonXmlExample.getErrorResponse("Internal server error", 500));
        }
    }

    // Новые методы для обработки API endpoints
    private void handleValidateToken(HttpRequest request, HttpResponse response) {
        try {
            String requestBody = request.getBody();
            Map<String, Object> body = parseJsonBody(requestBody);

            String token = (String) body.get("token");
            SimpleTokenUtils.TokenData tokenData = SimpleTokenUtils.validateToken(token);

            Map<String, Object> result = new HashMap<>();
            result.put("valid", tokenData != null);
            if (tokenData != null) {
                result.put("user_id", tokenData.getUserId());
                result.put("username", tokenData.getUsername());
            }

            response.setStatusCode(200);
            response.addHeader(HttpHeader.CONTENT_TYPE, ContentType.APPLICATION_JSON_UTF8);
            response.setBody(JsonXmlExample.toJson(result));

        } catch (Exception e) {
            response.setStatusCode(400);
            response.addHeader(HttpHeader.CONTENT_TYPE, ContentType.APPLICATION_JSON_UTF8);
            response.setBody(JsonXmlExample.getErrorResponse("Invalid request", 400));
        }
    }

    private void handleRefreshToken(HttpRequest request, HttpResponse response) {
        try {
            // Получаем refresh token из cookie
            String refreshToken = getCookieValue(request, "refresh_token");

            if (refreshToken != null && validateRefreshToken(refreshToken)) {
                int userId = getUserIdFromRefreshToken(refreshToken);
                String username = getUsernameById(userId);
                String newToken = SimpleTokenUtils.generateToken(userId, username);

                Map<String, Object> result = new HashMap<>();
                result.put("token", newToken);
                result.put("user_id", userId);
                result.put("username", username);

                response.setStatusCode(200);
                response.addHeader(HttpHeader.CONTENT_TYPE, ContentType.APPLICATION_JSON_UTF8);
                response.setBody(JsonXmlExample.toJson(result));
            } else {
                response.setStatusCode(401);
                response.addHeader(HttpHeader.CONTENT_TYPE, ContentType.APPLICATION_JSON_UTF8);
                response.setBody(JsonXmlExample.getErrorResponse("Invalid refresh token", 401));
            }

        } catch (Exception e) {
            response.setStatusCode(500);
            response.addHeader(HttpHeader.CONTENT_TYPE, ContentType.APPLICATION_JSON_UTF8);
            response.setBody(JsonXmlExample.getErrorResponse("Internal server error", 500));
        }
    }

    // Вспомогательные методы
    private Map<String, Object> parseJsonBody(String body) {
        try {
            return JsonXmlExample.parseJson(body);
        } catch (Exception e) {
            throw new RuntimeException("Invalid JSON");
        }
    }

    private String getCookieValue(HttpRequest request, String cookieName) {
        String cookieHeader = request.getHeaders().get("Cookie");
        if (cookieHeader != null) {
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=");
                if (parts.length == 2 && parts[0].equals(cookieName)) {
                    return parts[1];
                }
            }
        }
        return null;
    }

    private boolean validateRefreshToken(String refreshToken) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "SELECT user_id, expires_at FROM user_sessions WHERE token = ? AND expires_at > NOW() AND is_active = TRUE";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, refreshToken);
                ResultSet rs = stmt.executeQuery();
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private int getUserIdFromRefreshToken(String refreshToken) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "SELECT user_id FROM user_sessions WHERE token = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, refreshToken);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("user_id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private String getUsernameById(int userId) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "SELECT user_name FROM users WHERE user_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("user_name");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Unknown";
    }

    private void sendResponse(AsynchronousSocketChannel channel, HttpResponse response) {
        try {
            ByteBuffer resp = ByteBuffer.wrap(response.getBytes());
            channel.write(resp).get();
        } catch (Exception e) {
            System.err.println("Error sending response: " + e.getMessage());
        }
    }

    private void sendErrorResponse(AsynchronousSocketChannel channel, int code, String message) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(code);
        response.setStatus(message);
        response.addHeader(HttpHeader.CONTENT_TYPE, ContentType.TEXT_PLAIN_UTF8);
        response.setBody(message);
        sendResponse(channel, response);
    }

    // Метод для преобразования AsynchronousSocketChannel в Socket
    private Socket asyncChannelToSocket(AsynchronousSocketChannel asyncChannel) {
        try {
            InetSocketAddress remoteAddress = (InetSocketAddress) asyncChannel.getRemoteAddress();
            Socket socket = new Socket();
            socket.setReuseAddress(true);
            return socket;
        } catch (IOException e) {
            System.err.println("Error creating socket from async channel: " + e.getMessage());
            return null;
        }
    }
    // ДОБАВЬТЕ эти методы в класс WebSocketMessageProcessor

    private String handleJsonJoinChat(Map<String, Object> messageData, WebSocketServer.WebSocketClient wsClient) {
        if (!wsClient.isAuthenticated()) {
            return "{\"type\":\"error\",\"message\":\"Not authenticated\"}";
        }

        String chatId = (String) messageData.get("chat_id");
        if (chatId == null) {
            return "{\"type\":\"error\",\"message\":\"Missing chat_id\"}";
        }

        int userId = Integer.parseInt(wsClient.getUserId());

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Проверяем доступ к чату
            String checkSql = "SELECT 1 FROM chat_members cm " +
                    "WHERE cm.chat_id = ? AND cm.user_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, Integer.parseInt(chatId));
                checkStmt.setInt(2, userId);
                if (!checkStmt.executeQuery().next()) {
                    return "{\"type\":\"error\",\"message\":\"Access denied to chat\"}";
                }
            }

            // Получаем информацию о чате
            String chatInfoSql = "SELECT chat_name, chat_type FROM chats WHERE chat_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(chatInfoSql)) {
                stmt.setInt(1, Integer.parseInt(chatId));
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    String chatName = rs.getString("chat_name");
                    String chatType = rs.getString("chat_type");

                    wsClient.setCurrentChatId(chatId);

                    Map<String, Object> response = new HashMap<>();
                    response.put("type", "joined_chat");
                    response.put("chat_id", chatId);
                    response.put("chat_name", chatName);
                    response.put("chat_type", chatType);

                    return JsonBuilder.build(response);
                }
            }

            return "{\"type\":\"error\",\"message\":\"Chat not found\"}";

        } catch (Exception e) {
            e.printStackTrace();
            return "{\"type\":\"error\",\"message\":\"Failed to join chat\"}";
        }
    }

    private String handleGetChatsJson(WebSocketServer.WebSocketClient wsClient) {
        if (!wsClient.isAuthenticated()) {
            return "{\"type\":\"error\",\"message\":\"Not authenticated\"}";
        }

        int userId = Integer.parseInt(wsClient.getUserId());

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = """
            SELECT c.chat_id, c.chat_name, c.chat_type, 
                   (SELECT m.message_text FROM messages m 
                    WHERE m.chat_id = c.chat_id 
                    ORDER BY m.sent_at DESC LIMIT 1) as last_message,
                   (SELECT m.sent_at FROM messages m 
                    WHERE m.chat_id = c.chat_id 
                    ORDER BY m.sent_at DESC LIMIT 1) as last_message_time,
                   (SELECT COUNT(*) FROM messages m 
                    WHERE m.chat_id = c.chat_id AND m.sent_at > (
                        SELECT COALESCE(MAX(rr.read_at), '1970-01-01') 
                        FROM read_receipts rr 
                        JOIN messages m2 ON rr.message_id = m2.message_id 
                        WHERE m2.chat_id = c.chat_id AND rr.user_id = ?
                    )) as unread_count
            FROM chats c
            JOIN chat_members cm ON c.chat_id = cm.chat_id
            WHERE cm.user_id = ?
            ORDER BY last_message_time DESC NULLS LAST
            """;

            List<Map<String, Object>> chats = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, userId);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    Map<String, Object> chat = new HashMap<>();
                    chat.put("chat_id", rs.getInt("chat_id"));
                    chat.put("chat_name", rs.getString("chat_name"));
                    chat.put("chat_type", rs.getString("chat_type"));
                    chat.put("last_message", rs.getString("last_message"));
                    chat.put("last_message_time", rs.getTimestamp("last_message_time"));
                    chat.put("unread_count", rs.getInt("unread_count"));
                    chats.add(chat);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("type", "chats");
            response.put("chats", chats);

            return JsonBuilder.build(response);

        } catch (Exception e) {
            e.printStackTrace();
            return "{\"type\":\"error\",\"message\":\"Failed to get chats\"}";
        }
    }

    private String handleGetMessagesJson(Map<String, Object> messageData, WebSocketServer.WebSocketClient wsClient) {
        if (!wsClient.isAuthenticated()) {
            return "{\"type\":\"error\",\"message\":\"Not authenticated\"}";
        }

        String chatId = (String) messageData.get("chat_id");
        if (chatId == null) {
            return "{\"type\":\"error\",\"message\":\"Missing chat_id\"}";
        }

        int userId = Integer.parseInt(wsClient.getUserId());

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Проверяем доступ к чату
            String checkSql = "SELECT 1 FROM chat_members WHERE chat_id = ? AND user_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, Integer.parseInt(chatId));
                checkStmt.setInt(2, userId);
                if (!checkStmt.executeQuery().next()) {
                    return "{\"type\":\"error\",\"message\":\"Access denied\"}";
                }
            }

            String sql = """
            SELECT m.message_id, m.message_text, m.sent_at, 
                   u.user_id as sender_id, u.user_name as sender_name,
                   (u.user_id = ?) as is_own
            FROM messages m
            JOIN users u ON m.sender_id = u.user_id
            WHERE m.chat_id = ? AND m.is_deleted = false
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
                    message.put("sender_id", rs.getInt("sender_id"));
                    message.put("sender_name", rs.getString("sender_name"));
                    message.put("text", rs.getString("message_text"));
                    message.put("time", rs.getTimestamp("sent_at"));
                    message.put("is_own", rs.getBoolean("is_own"));
                    messages.add(message);
                }
            }

            // Обновляем время последнего прочтения
            updateReadReceipts(conn, userId, Integer.parseInt(chatId));

            Map<String, Object> response = new HashMap<>();
            response.put("type", "messages");
            response.put("chat_id", chatId);
            response.put("messages", messages);

            return JsonBuilder.build(response);

        } catch (Exception e) {
            e.printStackTrace();
            return "{\"type\":\"error\",\"message\":\"Failed to get messages\"}";
        }
    }

    private void updateReadReceipts(Connection conn, int userId, int chatId) throws SQLException {
        String sql = """
        INSERT INTO read_receipts (message_id, user_id, read_at)
        SELECT m.message_id, ?, NOW()
        FROM messages m
        WHERE m.chat_id = ? AND m.sent_at <= NOW() AND m.is_deleted = false
        AND NOT EXISTS (
            SELECT 1 FROM read_receipts rr 
            WHERE rr.message_id = m.message_id AND rr.user_id = ?
        )
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, chatId);
            stmt.setInt(3, userId);
            stmt.executeUpdate();
        }
    }
}