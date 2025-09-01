import security.SimpleTokenUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class WebSocketServer {
    private final int port;
    private final ConcurrentHashMap<AsynchronousSocketChannel, WebSocketClient> clients = new ConcurrentHashMap<>();
    private final MessageProcessor messageProcessor;
    private AsynchronousServerSocketChannel server;

    private final List<String> allowedOrigins = Arrays.asList(
            "http://192.168.100.5:8088",
            "http://192.168.100.5:3000"
    );

    public WebSocketServer(int port) {
        this.port = port;
        this.messageProcessor = new WebSocketMessageProcessor(this);
    }
    public void handleWebSocketConnection(AsynchronousSocketChannel client, String requestData) {
        try {
            if (isWebSocketHandshake(requestData)) {
                if (!isOriginAllowed(requestData)) {
                    sendForbiddenResponse(client);
                    cleanupClient(client);
                    return;
                }

                handleWebSocketHandshake(client, requestData);
                clients.put(client, new WebSocketClient());
                startReading(client);
            } else {
                cleanupClient(client);
            }
        } catch (Exception e) {
            e.printStackTrace();
            cleanupClient(client);
        }
    }
    public void start() {
        try {
            server = AsynchronousServerSocketChannel.open();
            server.bind(new InetSocketAddress("0.0.0.0", port));

            System.out.println("WebSocket server started on port " + port);

            server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
                @Override
                public void completed(AsynchronousSocketChannel client, Void attachment) {
                    server.accept(null, this);
                    handleClient(client);
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    System.err.println("Failed to accept connection: " + exc.getMessage());
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(AsynchronousSocketChannel client) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(2048);
            client.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer result, ByteBuffer buffer) {
                    if (result == -1) {
                        cleanupClient(client);
                        return;
                    }

                    buffer.flip();
                    String request = StandardCharsets.UTF_8.decode(buffer).toString();

                    // Проверяем WebSocket handshake
                    if (isWebSocketHandshake(request)) {
                        // Проверяем origin
                        if (!isOriginAllowed(request)) {
                            sendForbiddenResponse(client);
                            cleanupClient(client);
                            return;
                        }

                        String token = extractTokenFromRequest(request);
                        System.out.println("Extracted token: " + (token != null ? token.substring(0, 20) + "..." : "null"));  WebSocketClient wsClient = new WebSocketClient();

                        if (token != null) {
                            SimpleTokenUtils.TokenData tokenData = SimpleTokenUtils.validateToken(token);
                            System.out.println("Token validation result: " + (tokenData != null ? "VALID" : "INVALID"));
                            if (tokenData != null) {
                                wsClient.setAuthenticated(true);
                                wsClient.setUserId(String.valueOf(tokenData.getUserId()));
                            }
                        }

                        try {
                            handleWebSocketHandshake(client, request);
                            clients.put(client, wsClient);
                            startReading(client);
                        } catch (Exception e) {
                            e.printStackTrace();
                            cleanupClient(client);
                        }
                    } else {
                        // Не WebSocket запрос
                        cleanupClient(client);
                    }
                }

                @Override
                public void failed(Throwable exc, ByteBuffer buffer) {
                    cleanupClient(client);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            cleanupClient(client);
        }
    }
    private String extractTokenFromRequest(String request) {
        System.out.println("Request: " + request);

        if (request.contains("GET /")) {
            String[] lines = request.split("\r\n");
            String firstLine = lines[0];

            // 1. Проверяем query string (приоритет)
            if (firstLine.contains("?")) {
                String queryString = firstLine.split("\\?")[1].split(" ")[0];
                System.out.println("Query string: " + queryString);

                String[] params = queryString.split("&");
                for (String param : params) {
                    if (param.startsWith("token=")) {
                        String tokenValue = param.substring(6);
                        System.out.println("Found token in query: " + tokenValue);
                        return tokenValue;
                    }
                }
            }

            // 2. Проверяем cookies (второй приоритет)
            for (String line : lines) {
                if (line.startsWith("Cookie:")) {
                    String cookieHeader = line.substring("Cookie:".length()).trim();
                    System.out.println("Cookie header: " + cookieHeader);

                    String[] cookies = cookieHeader.split(";");
                    for (String cookie : cookies) {
                        cookie = cookie.trim();
                        if (cookie.startsWith("auth_token=")) { // ФИКС: меняем token= на auth_token=
                            String tokenValue = cookie.substring("auth_token=".length());
                            System.out.println("Found token in cookie: " + tokenValue);
                            return tokenValue;
                        }
                    }
                }
            }

            // 3. Проверяем Authorization header (третий приоритет)
            for (String line : lines) {
                if (line.startsWith("Authorization:")) {
                    String authHeader = line.substring("Authorization:".length()).trim();
                    if (authHeader.startsWith("Bearer ")) {
                        String tokenValue = authHeader.substring(7);
                        System.out.println("Found token in Authorization header: " + tokenValue);
                        return tokenValue;
                    }
                }
            }
        }

        System.out.println("No token found in request");
        return null;
    }

    private boolean isWebSocketHandshake(String request) {
        return request.contains("Upgrade: websocket") &&
                request.contains("Sec-WebSocket-Key");
    }

    private boolean isOriginAllowed(String request) {
        String origin = extractOrigin(request);
        if (origin == null) {
            System.out.println("Origin header missing, allowing for development");
            return true;
        }

        for (String allowedOrigin : allowedOrigins) {
            if (origin.equals(allowedOrigin)) {
                return true;
            }
        }

        System.out.println("Origin not allowed: " + origin);
        return false;
    }


    private String extractOrigin(String request) {
        String[] lines = request.split("\r\n");
        for (String line : lines) {
            if (line.startsWith("Origin:")) {
                return line.substring("Origin:".length()).trim();
            }
        }
        return null;
    }

    private void sendForbiddenResponse(AsynchronousSocketChannel client) {
        try {
            String response = "HTTP/1.1 403 Forbidden\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    "Origin not allowed";
            client.write(ByteBuffer.wrap(response.getBytes())).get();
        } catch (Exception e) {
            System.err.println("Error sending forbidden response: " + e.getMessage());
        }
    }


    private void handleWebSocketHandshake(AsynchronousSocketChannel client, String request) throws Exception {
        String key = extractWebSocketKey(request);
        String acceptKey = generateAcceptKey(key);
        String origin = extractOrigin(request);

        System.out.println("WebSocket handshake from origin: " + origin);

        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: " + acceptKey + "\r\n";

        if (origin != null) {
            response += "Access-Control-Allow-Origin: " + origin + "\r\n";
            response += "Access-Control-Allow-Credentials: true\r\n";
        }

        response += "\r\n";

        client.write(ByteBuffer.wrap(response.getBytes())).get();
        System.out.println("WebSocket handshake completed");
    }


    private String extractWebSocketKey(String request) {
        String[] lines = request.split("\r\n");
        for (String line : lines) {
            if (line.startsWith("Sec-WebSocket-Key:")) {
                return line.substring("Sec-WebSocket-Key:".length()).trim();
            }
        }
        return null;
    }

    private String generateAcceptKey(String key) throws Exception {
        String magicString = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] digest = sha1.digest(magicString.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    private void startReading(AsynchronousSocketChannel client) {
        ByteBuffer buffer = ByteBuffer.allocate(4096);

        client.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer result, ByteBuffer buffer) {
                if (result == -1) {
                    cleanupClient(client);
                    return;
                }

                try {
                    buffer.flip();
                    processWebSocketFrame(client, buffer);
                } catch (Exception e) {
                    e.printStackTrace();
                    cleanupClient(client);
                    return;
                }

                buffer.clear();
                client.read(buffer, buffer, this);
            }

            @Override
            public void failed(Throwable exc, ByteBuffer buffer) {
                cleanupClient(client);
            }
        });
    }

    private void processWebSocketFrame(AsynchronousSocketChannel client, ByteBuffer buffer) throws Exception {
        byte firstByte = buffer.get();
        byte secondByte = buffer.get();

        int opcode = firstByte & 0x0F;
        boolean masked = (secondByte & 0x80) != 0;
        int payloadLength = secondByte & 0x7F;

        if (payloadLength == 126) {
            payloadLength = buffer.getShort() & 0xFFFF;
        } else if (payloadLength == 127) {
            payloadLength = (int) buffer.getLong();
        }

        byte[] maskingKey = new byte[4];
        if (masked) {
            buffer.get(maskingKey);
        }

        byte[] payload = new byte[payloadLength];
        buffer.get(payload);

        // Демаскировка
        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= maskingKey[i % 4];
            }
        }

        if (opcode == 1) { // Text frame
            String message = new String(payload, StandardCharsets.UTF_8);
            handleWebSocketMessage(client, message);
        } else if (opcode == 8) { // Close frame
            cleanupClient(client);
        } else if (opcode == 9) { // Ping frame
            sendPong(client, payload);
        } else if (opcode == 10) { // Pong frame
            // Игнорируем
        }
    }

    private void handleWebSocketMessage(AsynchronousSocketChannel client, String message) {
        try {
            System.out.println("Received WebSocket message: " + message);

            WebSocketClient wsClient = clients.get(client);
            if (messageProcessor != null && wsClient != null) {
                String response = messageProcessor.processMessage(client, message, wsClient);
                if (response != null) {
                    sendWebSocketMessage(client, response);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendWebSocketMessage(AsynchronousSocketChannel client, String message) {
        try {
            byte[] payload = message.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(payload.length + 10);

            buffer.put((byte) 0x81); // FIN + Text frame

            if (payload.length <= 125) {
                buffer.put((byte) payload.length);
            } else if (payload.length <= 65535) {
                buffer.put((byte) 126);
                buffer.putShort((short) payload.length);
            } else {
                buffer.put((byte) 127);
                buffer.putLong(payload.length);
            }

            buffer.put(payload);
            buffer.flip();

            client.write(buffer);

        } catch (Exception e) {
            e.printStackTrace();
            cleanupClient(client);
        }
    }

    private void sendPong(AsynchronousSocketChannel client, byte[] payload) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(payload.length + 10);
            buffer.put((byte) 0x8A); // Pong frame
            buffer.put((byte) payload.length);
            buffer.put(payload);
            buffer.flip();
            client.write(buffer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cleanupClient(AsynchronousSocketChannel client) {
        try {
            clients.remove(client);
            client.close();
            System.out.println("WebSocket client disconnected");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void broadcastToChat(String chatId, String message) {
        int sentCount = 0;
        List<AsynchronousSocketChannel> clientsToProcess = new ArrayList<>(clients.keySet());

        for (AsynchronousSocketChannel client : clientsToProcess) {
            WebSocketClient wsClient = clients.get(client);

            if (wsClient != null &&
                    wsClient.isAuthenticated() &&
                    chatId.equals(wsClient.getCurrentChatId()) &&
                    client.isOpen()) {

                try {
                    sendWebSocketMessage(client, message);
                    sentCount++;
                } catch (Exception e) {
                    System.err.println("Failed to send message to client: " + e.getMessage());
                    cleanupClient(client);
                }
            }
        }
        System.out.println("Broadcasted to " + sentCount + " clients in chat: " + chatId);
    }

    public interface MessageProcessor {
        String processMessage(AsynchronousSocketChannel client, String message, WebSocketClient wsClient);
    }

    public static class WebSocketClient {
        private boolean authenticated = false;
        private String userId;
        private String currentChatId;

        public boolean isAuthenticated() { return authenticated; }
        public void setAuthenticated(boolean authenticated) { this.authenticated = authenticated; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getCurrentChatId() { return currentChatId; }
        public void setCurrentChatId(String currentChatId) { this.currentChatId = currentChatId; }

        public void setUsername(String username) {
        }
    }
}