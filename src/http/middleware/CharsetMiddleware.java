package http.middleware;

import http.httpdiff.HttpRequest;
import http.httpdiff.HttpResponse;

public class CharsetMiddleware implements Middleware {
    private static final String UTF8_CHARSET = "; charset=UTF-8";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    @Override
    public boolean handle(HttpRequest request, HttpResponse response) {
        String contentType = response.getHeaders().get(CONTENT_TYPE_HEADER);

        if (shouldAddCharset(contentType)) {
            response.addHeader(CONTENT_TYPE_HEADER, contentType + UTF8_CHARSET);
        }

        return true;
    }

    private boolean shouldAddCharset(String contentType) {
        if (contentType == null || contentType.contains("charset")) {
            return false;
        }

        String lowerContentType = contentType.toLowerCase();
        return lowerContentType.startsWith("text/") ||
                lowerContentType.contains("application/json") ||
                lowerContentType.contains("application/xml") ||
                lowerContentType.contains("application/javascript") ||
                lowerContentType.contains("application/x-www-form-urlencoded");
    }
}