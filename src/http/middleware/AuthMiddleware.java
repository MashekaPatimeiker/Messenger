package http.middleware;

import http.httpdiff.HttpRequest;
import http.httpdiff.HttpResponse;
import security.SimpleTokenUtils;

import java.util.Arrays;

public class AuthMiddleware implements Middleware {
    @Override
    public boolean handle(HttpRequest request, HttpResponse response) {
        if (request.getUrl().startsWith("/api/") &&
                !request.getUrl().equals("/api/login") &&
                !request.getUrl().equals("/api/register") &&
                !request.getUrl().equals("/api/check-auth")) {

            String token = getAuthTokenFromRequest(request);
            int userId = SimpleTokenUtils.getUserIdFromToken(token);

            if (userId == -1) {
                response.setStatusCode(401);
                response.addHeader("Content-Type", "application/json");
                response.setBody("{\"error\": \"Unauthorized\"}");
                return false;
            }

            request.getHeaders().put("X-User-ID", String.valueOf(userId));
        }
        return true;
    }

    private String getAuthTokenFromRequest(HttpRequest req) {
        String authHeader = req.getHeaders().getOrDefault("Authorization", "");
        if (authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        String cookieHeader = req.getHeaders().getOrDefault("Cookie", "");
        return Arrays.stream(cookieHeader.split(";"))
                .map(String::trim)
                .filter(c -> c.startsWith("auth_token="))
                .map(c -> c.substring("auth_token=".length()))
                .findFirst()
                .orElse("");
    }
}