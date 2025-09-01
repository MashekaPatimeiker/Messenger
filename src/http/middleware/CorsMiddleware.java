package http.middleware;

import http.httpdiff.HttpRequest;
import http.httpdiff.HttpResponse;

public class CorsMiddleware implements Middleware {
    @Override
    public boolean handle(HttpRequest request, HttpResponse response) {
        response.addHeader("Access-Control-Allow-Origin", "http://localhost:8088"); // Укажите ваш фронтенд URL
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept, X-Requested-With");
        response.addHeader("Access-Control-Allow-Credentials", "true");
        response.addHeader("Access-Control-Max-Age", "3600");


        return true;
    }
}