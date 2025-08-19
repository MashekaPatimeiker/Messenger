package http.httpdiff;

import java.io.IOException;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.net.URLDecoder;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class HttpRequest {
    private final static String DELIMITER = "\r\n\r\n";
    private final static String NEW_LINE = "\r\n";
    private final static String HEADER_DELIMITER = ":";
    private final Map<String, String> params = new HashMap<>();
    private final String message;
    private final HttpMethod method;
    private final String url;
    private final Map<String, String> headers;
    private final String body;
    private final Map<String, String> queryParams;
    private final String pathWithoutQuery;
    private Socket socket;

    public HttpRequest(String message, Socket socket) {
        this.socket = socket;
        this.message = message;

        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("Empty HTTP request");
        }
        String[] parts = message.split(DELIMITER, 2);
        String head = parts[0];

        String[] headers = head.split(NEW_LINE);
        if (headers.length == 0) {
            throw new IllegalArgumentException("Invalid HTTP request: no headers");
        }

        String[] firstLine = headers[0].split(" ");
        if (firstLine.length < 2) {
            throw new IllegalArgumentException("Invalid HTTP start line");
        }

        try {
            this.method = HttpMethod.valueOf(firstLine[0]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + firstLine[0]);
        }

        this.url = firstLine[1];

        // Парсим query parameters при создании объекта
        this.queryParams = parseQueryParams(this.url);
        this.pathWithoutQuery = extractPathWithoutQuery(this.url);

        Map<String, String> headersMap = new HashMap<>();
        for (int i = 1; i < headers.length; i++) {
            String[] headerParts = headers[i].split(HEADER_DELIMITER, 2);
            if (headerParts.length == 2) {
                headersMap.put(headerParts[0].trim(), headerParts[1].trim());
            }
        }
        this.headers = Collections.unmodifiableMap(headersMap);

        String bodyContent = parts.length > 1 ? parts[1] : "";
        String contentLength = this.headers.get(HttpHeader.CONTENT_LENGTH);

        if (contentLength != null) {
            try {
                int length = Integer.parseInt(contentLength);
                this.body = bodyContent.length() > length
                        ? bodyContent.substring(0, length)
                        : bodyContent;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid Content-Length header");
            }
        } else {
            this.body = bodyContent;
        }
    }

    public HttpRequest(String message) {
        this(message, null); // Socket может быть null для некоторых случаев
    }

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    // Остальные методы без изменений...
    private Map<String, String> parseQueryParams(String url) {
        Map<String, String> params = new HashMap<>();

        int questionMarkIndex = url.indexOf('?');
        if (questionMarkIndex == -1) {
            return params;
        }

        String queryString = url.substring(questionMarkIndex + 1);
        if (queryString.isEmpty()) {
            return params;
        }

        try {
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                    params.put(key, value);
                } else if (keyValue.length == 1) {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                    params.put(key, "");
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing query parameters: " + e.getMessage());
        }

        return Collections.unmodifiableMap(params);
    }

    private String extractPathWithoutQuery(String url) {
        int questionMarkIndex = url.indexOf('?');
        if (questionMarkIndex == -1) {
            return url;
        }
        return url.substring(0, questionMarkIndex);
    }

    public String getQueryString() {
        int questionMarkIndex = url.indexOf('?');
        if (questionMarkIndex == -1) {
            return null;
        }
        return url.substring(questionMarkIndex + 1);
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public String getQueryParam(String paramName) {
        return queryParams.get(paramName);
    }

    public String getQueryParam(String paramName, String defaultValue) {
        return queryParams.getOrDefault(paramName, defaultValue);
    }

    public boolean hasQueryParam(String paramName) {
        return queryParams.containsKey(paramName);
    }

    public String getPathWithoutQuery() {
        return pathWithoutQuery;
    }
    // В классе HttpRequest добавьте метод:
    public AsynchronousSocketChannel getChannel() throws IOException {
        if (socket != null && socket.isConnected()) {
            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
            // Можно также передать настройки сокета
            channel.setOption(StandardSocketOptions.SO_KEEPALIVE, socket.getKeepAlive());
            channel.setOption(StandardSocketOptions.TCP_NODELAY, socket.getTcpNoDelay());
            return channel;
        }
        return null;
    }
    public void addParam(String name, String value) {
        params.put(name, value);
    }

    public Map<String, String> getParams() {
        return Collections.unmodifiableMap(params);
    }

    public String getParam(String name) {
        return params.get(name);
    }

    public String getMessage() { return message; }
    public HttpMethod getMethod() { return method; }
    public String getUrl() { return url; }
    public Map<String, String> getHeaders() { return headers; }
    public String getBody() { return body; }
}