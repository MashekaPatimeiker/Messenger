package security;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class SimpleTokenUtils {
    private static final Map<String, TokenData> tokenStore = new ConcurrentHashMap<>();
    private static final long EXPIRATION_TIME = 86400000; // 24 часа
    private static final String TOKEN_PREFIX = "token_";

    public static class TokenData {
        private int userId;
        private String username;
        private long expirationTime;

        public TokenData(int userId, String username) {
            this.userId = userId;
            this.username = username;
            this.expirationTime = System.currentTimeMillis() + EXPIRATION_TIME;
        }

        public int getUserId() { return userId; }
        public String getUsername() { return username; }
        public boolean isValid() { return System.currentTimeMillis() < expirationTime; }
        public long getExpirationTime() { return expirationTime; }
    }

    public static String generateToken(int userId, String username) {
        String token = TOKEN_PREFIX + UUID.randomUUID().toString() + "_" + System.currentTimeMillis();
        tokenStore.put(token, new TokenData(userId, username));
        return Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    public static TokenData validateToken(String encodedToken) {
        if (encodedToken == null || encodedToken.isEmpty()) {
            return null;
        }

        try {
            // Декодируем URL-encoded строку (убираем %3D и т.д.)
            String decodedToken = URLDecoder.decode(encodedToken, StandardCharsets.UTF_8.name());

            String token = new String(Base64.getDecoder().decode(decodedToken), StandardCharsets.UTF_8);

            // Проверяем, что токен начинается с правильного префикса
            if (!token.startsWith(TOKEN_PREFIX)) {
                return null;
            }

            TokenData tokenData = tokenStore.get(token);
            if (tokenData != null && tokenData.isValid()) {
                return tokenData;
            }

            // Удаляем просроченный токен
            if (tokenData != null) {
                tokenStore.remove(token);
            }
            return null;

        } catch (Exception e) {
            System.err.println("Token validation error: " + e.getMessage());
            return null;
        }
    }

    public static Map<String, Object> verifyToken(String encodedToken) {
        TokenData tokenData = validateToken(encodedToken);
        if (tokenData == null) {
            throw new RuntimeException("Invalid or expired token");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", tokenData.getUserId());
        claims.put("username", tokenData.getUsername());
        claims.put("exp", tokenData.getExpirationTime());
        claims.put("iat", tokenData.getExpirationTime() - EXPIRATION_TIME);

        return claims;
    }

    public static int getUserIdFromToken(String token) {
        TokenData tokenData = validateToken(token);
        return tokenData != null ? tokenData.getUserId() : -1;
    }

    public static String getUsernameFromToken(String token) {
        TokenData tokenData = validateToken(token);
        return tokenData != null ? tokenData.getUsername() : null;
    }

    // Очистка просроченных токенов
    public static void cleanupExpiredTokens() {
        tokenStore.entrySet().removeIf(entry -> !entry.getValue().isValid());
    }

    // Новый метод для проверки формата токена без валидации
    public static boolean isTokenFormatValid(String encodedToken) {
        if (encodedToken == null || encodedToken.isEmpty()) {
            return false;
        }

        try {
            String decodedToken = URLDecoder.decode(encodedToken, StandardCharsets.UTF_8.name());
            String token = new String(Base64.getDecoder().decode(decodedToken), StandardCharsets.UTF_8);
            return token.startsWith(TOKEN_PREFIX);
        } catch (Exception e) {
            return false;
        }
    }
}