import json.JsonBuilder;
import json.JsonParser;
import security.SimpleTokenUtils;

import java.nio.channels.AsynchronousSocketChannel;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketMessageProcessor implements WebSocketServer.MessageProcessor {
    private final WebSocketServer webSocketServer;
    private final Map<AsynchronousSocketChannel, String> clientChats = new ConcurrentHashMap<>();

    // Данные БД
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgress";

    public WebSocketMessageProcessor(WebSocketServer webSocketServer) {
        this.webSocketServer = webSocketServer;
    }

    @Override
    public String processMessage(AsynchronousSocketChannel client, String message, WebSocketServer.WebSocketClient wsClient) {
        try {
            System.out.println("Processing WebSocket message: " + message);
            System.out.println("Processing WebSocket message: " + message);

            // Пробуем обработать как JSON
            if (message.trim().startsWith("{")) {
                return handleJsonMessage(client, message, wsClient);
            }

            // Обработка текстового протокола
            if (message.startsWith("AUTH:")) {
                return handleAuthMessage(client, message, wsClient);
            } else if (message.startsWith("MESSAGE:")) {
                return handleChatMessage(client, message, wsClient);
            }

            // Обработка текстового протокола
            if (message.startsWith("AUTH:")) {
                return handleAuthMessage(client, message, wsClient);
            } else if (message.startsWith("JOIN_CHAT:")) {
                return handleJoinChat(client, message, wsClient);
            } else if (message.startsWith("MESSAGE:")) {
                return handleChatMessage(client, message, wsClient);
            } else if (message.startsWith("GET_CHATS")) {
                return handleGetChats(wsClient);
            } else if (message.startsWith("GET_MESSAGES:")) {
                return handleGetMessages(message, wsClient);
            } else if (message.equals("PING")) {
                return "PONG";
            }

            return "ERROR: Unknown message format";

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }

    // ДОБАВЬТЕ этот метод в класс WebSocketMessageProcessor
    private String handleJsonMessage(AsynchronousSocketChannel client, String message, WebSocketServer.WebSocketClient wsClient) {
        try {
            Map<String, Object> messageData = JsonParser.parse(message);
            String type = (String) messageData.get("type");

            if ("auth".equals(type)) {
                return handleJsonAuth(messageData, wsClient);
            } else if ("message".equals(type)) {
                return handleJsonMessageSend(messageData, wsClient);
            } else if ("join_chat".equals(type)) {
                return handleJsonJoinChat(messageData, wsClient,client);
            } else if ("get_chats".equals(type)) {
            //    return handleGetChatsJson(wsClient);
            } else if ("get_messages".equals(type)) {
              //  return handleGetMessagesJson(messageData, wsClient);
            }

            return "{\"type\":\"error\",\"message\":\"Unknown message type\"}";

        } catch (Exception e) {
            return "{\"type\":\"error\",\"message\":\"Invalid JSON format\"}";
        }
    }
    private String handleJsonJoinChat(Map<String, Object> messageData, WebSocketServer.WebSocketClient wsClient,AsynchronousSocketChannel client) {
        if (!wsClient.isAuthenticated()) {
            return "{\"type\":\"error\",\"message\":\"Not authenticated\"}";
        }

        try {
            Number chatIdNumber = (Number) messageData.get("chat_id");
            if (chatIdNumber == null) {
                return "{\"type\":\"error\",\"message\":\"Chat ID required\"}";
            }

            String chatId = String.valueOf(chatIdNumber.intValue());
            int userId = Integer.parseInt(wsClient.getUserId());

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                // Проверяем доступ к чату
                String checkSql = "SELECT 1 FROM chat_members WHERE chat_id = ? AND user_id = ?";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setInt(1, Integer.parseInt(chatId));
                    checkStmt.setInt(2, userId);
                    if (!checkStmt.executeQuery().next()) {
                        return "{\"type\":\"error\",\"message\":\"Access denied to chat\"}";
                    }
                }

                // Устанавливаем текущий чат для клиента
                wsClient.setCurrentChatId(chatId);
                clientChats.put(client, chatId);

                // Получаем информацию о чате
                String chatInfoSql = "SELECT chat_name, chat_type FROM chats WHERE chat_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(chatInfoSql)) {
                    stmt.setInt(1, Integer.parseInt(chatId));
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        String chatName = rs.getString("chat_name");
                        String chatType = rs.getString("chat_type");

                        Map<String, Object> response = new HashMap<>();
                        response.put("type", "chat_joined");
                        response.put("chat_id", chatId);
                        response.put("chat_name", chatName);
                        response.put("chat_type", chatType);

                        return JsonBuilder.build(response);
                    }
                }

                return "{\"type\":\"error\",\"message\":\"Chat not found\"}";

            } catch (Exception e) {
                e.printStackTrace();
                return "{\"type\":\"error\",\"message\":\"Database error\"}";
            }

        } catch (Exception e) {
            return "{\"type\":\"error\",\"message\":\"Invalid request format\"}";
        }
    }
    private String handleJsonMessageSend(Map<String, Object> messageData, WebSocketServer.WebSocketClient wsClient) {
        if (!wsClient.isAuthenticated()) {
            return "{\"type\":\"error\",\"message\":\"Not authenticated\"}";
        }

        String text = (String) messageData.get("text");
        Number chatIdNumber = (Number) messageData.get("chat_id");

        if (text == null || text.trim().isEmpty()) {
            return "{\"type\":\"error\",\"message\":\"Message text is empty\"}";
        }

        if (chatIdNumber == null) {
            return "{\"type\":\"error\",\"message\":\"Chat ID is required\"}";
        }

        String chatId = String.valueOf(chatIdNumber.intValue());
        int userId = Integer.parseInt(wsClient.getUserId());

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Дополнительная проверка доступа к чату
            String checkSql = "SELECT 1 FROM chat_members WHERE chat_id = ? AND user_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, Integer.parseInt(chatId));
                checkStmt.setInt(2, userId);
                ResultSet rs = checkStmt.executeQuery();
                if (!rs.next()) {
                    return "{\"type\":\"error\",\"message\":\"Access denied to chat\"}";
                }
            }

            // Сохраняем сообщение в базу
            String insertSql = "INSERT INTO messages (chat_id, sender_id, message_text) VALUES (?, ?, ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                insertStmt.setInt(1, Integer.parseInt(chatId));
                insertStmt.setInt(2, userId);
                insertStmt.setString(3, text);
                insertStmt.executeUpdate();

                ResultSet rs = insertStmt.getGeneratedKeys();
                if (rs.next()) {
                    int messageId = rs.getInt(1);
                    Timestamp sentAt = getMessageTimestamp(conn, messageId);
                    String senderName = getUsernameById(conn, userId);

                    // Формируем JSON ответ
                    Map<String, Object> response = new HashMap<>();
                    response.put("type", "new_message");
                    response.put("message_id", messageId);
                    response.put("chat_id", chatId);
                    response.put("sender_id", userId);
                    response.put("sender_name", senderName);
                    response.put("text", text);
                    response.put("time", sentAt.toString());
                    response.put("is_own", true);

                    // Рассылаем всем участникам
                    String broadcastMessage = JsonBuilder.build(response);
                    webSocketServer.broadcastToChat(chatId, broadcastMessage);

                    return "{\"type\":\"message_sent\",\"message_id\":" + messageId + "}";
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "{\"type\":\"error\",\"message\":\"Database error: " + e.getMessage() + "\"}";
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"type\":\"error\",\"message\":\"Failed to send message: " + e.getMessage() + "\"}";
        }

        return "{\"type\":\"error\",\"message\":\"Failed to send message\"}";
    }
    private String handleJsonAuth(Map<String, Object> messageData, WebSocketServer.WebSocketClient wsClient) {
        String token = (String) messageData.get("token");
        SimpleTokenUtils.TokenData tokenData = SimpleTokenUtils.validateToken(token);

        if (tokenData != null) {
            wsClient.setAuthenticated(true);
            wsClient.setUserId(String.valueOf(tokenData.getUserId()));
            wsClient.setUsername(tokenData.getUsername());

            Map<String, Object> response = new HashMap<>();
            response.put("type", "auth");
            response.put("status", "success");
            response.put("user_id", tokenData.getUserId());
            response.put("username", tokenData.getUsername());

            try {
                return JsonBuilder.build(response);
            } catch (Exception e) {
                return "{\"type\":\"auth\",\"status\":\"success\",\"user_id\":" + tokenData.getUserId() + "}";
            }
        } else {
            return "{\"type\":\"auth\",\"status\":\"error\",\"message\":\"Invalid token\"}";
        }
    }

    private String handleAuthMessage(AsynchronousSocketChannel client, String message, WebSocketServer.WebSocketClient wsClient) {
        String token = message.substring(5).trim();
        SimpleTokenUtils.TokenData tokenData = SimpleTokenUtils.validateToken(token);

        if (tokenData != null) {
            wsClient.setAuthenticated(true);
            wsClient.setUserId(String.valueOf(tokenData.getUserId()));
            wsClient.setUsername(tokenData.getUsername());

            // Отправляем ответ в обоих форматах для совместимости
            return "AUTH_SUCCESS:" + tokenData.getUserId() + ":" + tokenData.getUsername();
        }

        return "AUTH_FAILED:Invalid token";
    }

    private String handleJoinChat(AsynchronousSocketChannel client, String message, WebSocketServer.WebSocketClient wsClient) {
        if (!wsClient.isAuthenticated()) {
            return "ERROR: Not authenticated";
        }

        String chatId = message.substring(10).trim();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            int userId = Integer.parseInt(wsClient.getUserId());

            // Проверяем доступ к чату
            String checkSql = "SELECT 1 FROM chat_members cm " +
                    "JOIN chats c ON cm.chat_id = c.chat_id " +
                    "WHERE cm.chat_id = ? AND cm.user_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, Integer.parseInt(chatId));
                checkStmt.setInt(2, userId);
                if (!checkStmt.executeQuery().next()) {
                    return "ERROR: Access denied to chat";
                }
            }

            wsClient.setCurrentChatId(chatId);
            clientChats.put(client, chatId);

            // Получаем информацию о чате
            String chatInfoSql = "SELECT chat_name, chat_type FROM chats WHERE chat_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(chatInfoSql)) {
                stmt.setInt(1, Integer.parseInt(chatId));
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    String chatName = rs.getString("chat_name");
                    String chatType = rs.getString("chat_type");
                    return "JOINED_CHAT:" + chatId + ":" + chatName + ":" + chatType;
                }
            }

            return "JOINED_CHAT:" + chatId;

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }

    private String handleChatMessage(AsynchronousSocketChannel client, String message, WebSocketServer.WebSocketClient wsClient) {
        if (!wsClient.isAuthenticated()) {
            return "ERROR: Not authenticated";
        }

        if (wsClient.getCurrentChatId() == null) {
            return "ERROR: Not in any chat";
        }

        String text = message.substring(8).trim();
        String chatId = wsClient.getCurrentChatId();
        int userId = Integer.parseInt(wsClient.getUserId());

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Сохраняем сообщение в базу данных
            String insertSql = "INSERT INTO messages (chat_id, sender_id, message_text) VALUES (?, ?, ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                insertStmt.setInt(1, Integer.parseInt(chatId));
                insertStmt.setInt(2, userId);
                insertStmt.setString(3, text);
                insertStmt.executeUpdate();

                ResultSet rs = insertStmt.getGeneratedKeys();
                if (rs.next()) {
                    int messageId = rs.getInt(1);
                    Timestamp sentAt = getMessageTimestamp(conn, messageId);

                    // Получаем информацию об отправителе
                    String senderName = getUsernameById(conn, userId);

                    // Формируем полное сообщение для рассылки
                    String broadcastMessage = String.format("NEW_MESSAGE:%d:%s:%s:%d:%s",
                            userId, senderName, text, messageId, sentAt.toString());

                    // Рассылаем всем участникам чата
                    webSocketServer.broadcastToChat(chatId, broadcastMessage);

                    return "MESSAGE_SENT:" + messageId;
                }
            }

            return "ERROR: Failed to save message";

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }

    private String handleGetChats(WebSocketServer.WebSocketClient wsClient) {
        if (!wsClient.isAuthenticated()) {
            return "ERROR: Not authenticated";
        }

        int userId = Integer.parseInt(wsClient.getUserId());

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = """
                SELECT c.chat_id, c.chat_name, c.chat_type, 
                       (SELECT message_text FROM messages 
                        WHERE chat_id = c.chat_id 
                        ORDER BY sent_at DESC LIMIT 1) as last_message,
                       (SELECT sent_at FROM messages 
                        WHERE chat_id = c.chat_id 
                        ORDER BY sent_at DESC LIMIT 1) as last_message_time,
                       (SELECT COUNT(*) FROM messages 
                        WHERE chat_id = c.chat_id AND sent_at > (
                            SELECT COALESCE(MAX(read_at), '1970-01-01') 
                            FROM read_receipts rr 
                            JOIN messages m ON rr.message_id = m.message_id 
                            WHERE m.chat_id = c.chat_id AND rr.user_id = ?
                        )) as unread_count
                FROM chats c
                JOIN chat_members cm ON c.chat_id = cm.chat_id
                WHERE cm.user_id = ?
                ORDER BY last_message_time DESC NULLS LAST
                """;

            StringBuilder response = new StringBuilder("CHATS:");
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, userId);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    response.append(rs.getInt("chat_id")).append(":")
                            .append(rs.getString("chat_name")).append(":")
                            .append(rs.getString("chat_type")).append(":")
                            .append(rs.getString("last_message")).append(":")
                            .append(rs.getTimestamp("last_message_time")).append(":")
                            .append(rs.getInt("unread_count")).append("|");
                }
            }

            return response.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }

    private String handleGetMessages(String message, WebSocketServer.WebSocketClient wsClient) {
        if (!wsClient.isAuthenticated()) {
            return "ERROR: Not authenticated";
        }

        String[] parts = message.split(":");
        if (parts.length < 2) {
            return "ERROR: Invalid format";
        }

        String chatId = parts[1];
        int userId = Integer.parseInt(wsClient.getUserId());

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Проверяем доступ к чату
            String checkSql = "SELECT * FROM chat_members WHERE chat_id = ? AND user_id = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, Integer.parseInt(chatId));
                checkStmt.setInt(2, userId);
                if (!checkStmt.executeQuery().next()) {
                    return "ERROR: Access denied";
                }
            }

            String sql = """
                SELECT m.message_id, m.message_text, m.sent_at, 
                       u.user_id as sender_id, u.user_name as sender_name,
                       (u.user_id = ?) as is_own_message
                FROM messages m
                JOIN users u ON m.sender_id = u.user_id
                WHERE m.chat_id = ? AND m.is_deleted = false
                ORDER BY m.sent_at ASC
                """;

            StringBuilder response = new StringBuilder("MESSAGES:" + chatId + ":");
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, userId);
                stmt.setInt(2, Integer.parseInt(chatId));
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    response.append(rs.getInt("message_id")).append(":")
                            .append(rs.getInt("sender_id")).append(":")
                            .append(rs.getString("sender_name")).append(":")
                            .append(rs.getString("message_text")).append(":")
                            .append(rs.getTimestamp("sent_at")).append(":")
                            .append(rs.getBoolean("is_own_message")).append("|");
                }
            }

            // Обновляем время последнего прочтения
            updateReadReceipts(conn, userId, Integer.parseInt(chatId));

            return response.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }

    private void updateReadReceipts(Connection conn, int userId, int chatId) throws SQLException {
        String sql = """
            INSERT INTO read_receipts (message_id, user_id, read_at)
            SELECT m.message_id, ?, NOW()
            FROM messages m
            WHERE m.chat_id = ? AND m.sent_at <= NOW()
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

    private String getUsernameById(Connection conn, int userId) throws SQLException {
        String sql = "SELECT user_name FROM users WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("user_name") : "Unknown";
        }
    }

    private Timestamp getMessageTimestamp(Connection conn, int messageId) throws SQLException {
        String sql = "SELECT sent_at FROM messages WHERE message_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, messageId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getTimestamp("sent_at") : new Timestamp(System.currentTimeMillis());
        }
    }
}