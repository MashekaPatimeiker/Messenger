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
        AsynchronousSocketChannel clientChannel = null;                  // Канал для работы с клиентом
        try {
            clientChannel = future.get();                               // Получаем канал клиента из Future
            System.out.println("New client connection");                // Логируем новое подключение

            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);       // Буфер для чтения данных
            StringBuilder builder = new StringBuilder();                // Накопитель запроса
            int totalBytesRead = 0;                                     // Счетчик прочитанных байт

            while (true) {                                              // Чтение данных в цикле
                int bytesRead = clientChannel.read(buffer).get();       // Читаем данные из канала
                if (bytesRead == -1) break;                             // Проверяем конец потока

                totalBytesRead += bytesRead;                             // Обновляем счетчик байт
                if (totalBytesRead > MAX_REQUEST_SIZE) {                 // Проверка на превышение лимита
                    sendErrorResponse(clientChannel, 413, "Request too large");  // Отправка ошибки 413
                    return;                                             // Завершаем обработку
                }

                buffer.flip();                                           // Подготавливаем буфер к чтению
                builder.append(StandardCharsets.UTF_8.decode(buffer));   // Декодируем данные в строку
                buffer.clear();                                          // Очищаем буфер для новых данных

                if (builder.toString().contains("\r\n\r\n")) {           // Проверяем конец HTTP-запроса
                    break;                                              // Выходим из цикла
                }
            }

            String requestData = builder.toString();                    // Получаем полный запрос
            if (requestData.isEmpty()) {                                // Проверка на пустой запрос
                sendErrorResponse(clientChannel, 400, "Empty request");  // Отправка ошибки 400
                return;                                                 // Завершаем обработку
            }

            try {
                HttpRequest request = new HttpRequest(requestData);     // Парсим HTTP-запрос
                HttpResponse response = new HttpResponse();              // Создаем HTTP-ответ

                if (handler != null) {                                  // Если есть обработчик,
                    handleRequest(request, response);                    // передаем ему запрос и ответ
                }

                sendResponse(clientChannel, response);                   // Отправляем ответ клиенту
            } catch (Exception e) {
                System.err.println("Error processing request: " + e.getMessage());  // Логируем ошибку
                sendErrorResponse(clientChannel, 400, "Bad request");    // Отправляем 400 при ошибке
            }
        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());  // Логируем ошибку соединения
        } finally {
            if (clientChannel != null) {
                try {
                    clientChannel.close();                               // Закрываем канал клиента
                } catch (IOException e) {
                    System.err.println("Error closing client channel: " + e.getMessage());  // Логируем ошибку закрытия
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