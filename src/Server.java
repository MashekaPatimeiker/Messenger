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
        AsynchronousSocketChannel clientChannel = null;
        try {
            clientChannel = future.get();
            System.out.println("New client connection");

            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            StringBuilder builder = new StringBuilder();
            int totalBytesRead = 0;

            while (true) {
                int bytesRead = clientChannel.read(buffer).get();
                if (bytesRead == -1) break;

                totalBytesRead += bytesRead;
                if (totalBytesRead > MAX_REQUEST_SIZE) {
                    sendErrorResponse(clientChannel, 413, "Request too large");
                    return;
                }

                buffer.flip();
                builder.append(StandardCharsets.UTF_8.decode(buffer));
                buffer.clear();

                if (builder.toString().contains("\r\n\r\n")) {
                    break;
                }
            }

            String requestData = builder.toString();
            if (requestData.isEmpty()) {
                sendErrorResponse(clientChannel, 400, "Empty request");
                return;
            }

            try {
                HttpRequest request = new HttpRequest(requestData);
                HttpResponse response = new HttpResponse();

                if (handler != null) {
                    handleRequest(request, response);
                }

                sendResponse(clientChannel, response);
            } catch (Exception e) {
                System.err.println("Error processing request: " + e.getMessage());
                sendErrorResponse(clientChannel, 400, "Bad request");
            }
        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            if (clientChannel != null) {
                try {
                    clientChannel.close();
                } catch (IOException e) {
                    System.err.println("Error closing client channel: " + e.getMessage());
                }
            }
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