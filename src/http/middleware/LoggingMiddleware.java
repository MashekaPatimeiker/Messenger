package http.middleware;

import http.httpdiff.HttpRequest;
import http.httpdiff.HttpResponse;

public class LoggingMiddleware implements Middleware {
    @Override
    public boolean handle(HttpRequest request, HttpResponse response) {
        System.out.printf("[%s] %s %s%n",
                java.time.LocalDateTime.now(),
                request.getMethod(),
                request.getUrl());
        return true;
    }
}