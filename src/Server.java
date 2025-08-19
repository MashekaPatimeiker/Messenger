import http.httpdiff.HttpHandler;
import http.httpdiff.HttpHeader;
import http.httpdiff.HttpRequest;
import http.httpdiff.HttpResponse;
import http.sitediff.ContentType;
import json.JsonXmlExample;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.net.Socket;
import java.util.concurrent.Future;

public class Server {
    private final static int BUFFER_SIZE = 8192;
    private final static int MAX_REQUEST_SIZE = 65536;

    private final HttpHandler handler;
    private final String host;
    private final int port;

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

            try {
                HttpRequest request = new HttpRequest(requestData);
                HttpResponse response = new HttpResponse();

                // Проверяем WebSocket запрос
                if (request.getUrl().equals("/ws") &&
                        "websocket".equalsIgnoreCase(request.getHeaders().getOrDefault("Upgrade", "")) &&
                        "Upgrade".equalsIgnoreCase(request.getHeaders().getOrDefault("Connection", ""))) {

                    isWebSocket = true;
                    System.out.println("WebSocket connection detected");
                    return; // Не закрываем соединение!
                }

                // Обычная HTTP обработка
                if (handler != null) {
                    handleRequest(request, response);
                }

                sendResponse(asyncChannel, response);

            } catch (Exception e) {
                System.err.println("Error processing request: " + e.getMessage());
                sendErrorResponse(asyncChannel, 400, "Bad request");
            }
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
        // Простая проверка на WebSocket запрос
        return requestData.contains("Upgrade: websocket") &&
                requestData.contains("Connection: Upgrade");
    }
    // Метод для преобразования AsynchronousSocketChannel в Socket
    private Socket asyncChannelToSocket(AsynchronousSocketChannel asyncChannel) {
        try {
            // Получаем удаленный адрес для создания Socket
            InetSocketAddress remoteAddress = (InetSocketAddress) asyncChannel.getRemoteAddress();
            // Создаем фиктивный Socket с правильными адресами
            Socket socket = new Socket();
            socket.setReuseAddress(true);
            // Важно: это упрощенное преобразование, для WebSocket этого должно быть достаточно
            return socket;
        } catch (IOException e) {
            System.err.println("Error creating socket from async channel: " + e.getMessage());
            return null;
        }
    }

    private void handleRequest(HttpRequest request, HttpResponse response) {
        try {
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
}