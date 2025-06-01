package http.httpdiff;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class HttpRequest {
    private final static String DELIMITER = "\r\n\r\n";
    private final static String NEW_LINE = "\r\n";
    private final static String HEADER_DELIMITER = ":";

    private final String message;
    private final HttpMethod method;
    private final String url;
    private final Map<String, String> headers;
    private final String body;

    public HttpRequest(String message) {
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

    public String getMessage() { return message; }
    public HttpMethod getMethod() { return method; }
    public String getUrl() { return url; }
    public Map<String, String> getHeaders() { return headers; }
    public String getBody() { return body; }
}