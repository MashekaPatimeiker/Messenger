package http.middleware;

import http.httpdiff.HttpRequest;
import http.httpdiff.HttpResponse;

public class CorsMiddleware implements Middleware {
    @Override
    public boolean handle(HttpRequest request, HttpResponse response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, *");
        response.addHeader("Access-Control-Expose-Headers", "*");

        return true;
    }
}