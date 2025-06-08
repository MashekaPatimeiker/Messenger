import http.httpdiff.HttpRequest;
import http.middleware.CharsetMiddleware;
import http.middleware.CorsMiddleware;
import http.middleware.LoggingMiddleware;
import http.sitediff.Router;
import http.sitediff.StaticFileHandler;
import json.JsonBuilder;
import json.JsonParser;
import json.JsonXmlExample;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

//привет
public class Main {
    private static boolean authEnabled;

    public static void main(String[] args) {
        try {
            config.Config.load("config/server.conf");
        } catch (IOException e) {
            System.out.println("Using default configuration");
        }
        authEnabled = config.Config.getBoolean("api.authEnabled");
        System.out.println("Authentication is " + (authEnabled ? "ENABLED" : "DISABLED"));

        Router router = new Router();
        router.use(new LoggingMiddleware());
        router.use(new CharsetMiddleware());

        if (config.Config.getBoolean("api.enableCors")) {
            router.use(new CorsMiddleware());
        }

        String staticFilesPath = config.Config.get("server.staticFiles");
        Path staticPath = Paths.get(staticFilesPath).toAbsolutePath();
        System.out.println("Static files path: " + staticPath);

        createDefaultFilesIfNotExist(staticPath);
        StaticFileHandler staticHandler = new StaticFileHandler(staticFilesPath);
        router.setStaticFileHandler(staticHandler);

        router.post("/api/echo", (req, res) -> {
            try {
                Map<String, Object> requestData = JsonParser.parse(req.getBody());
                String name = (String) requestData.getOrDefault("name", "Друг");
                String responseMessage = String.format("Привет, %s! Твой запрос успешно получен.", name);
                res.addHeader("Content-Type", "text/plain; charset=UTF-8");
                return responseMessage;
            } catch (Exception e) {
                res.setStatusCode(400);
                res.addHeader("Content-Type", "text/plain; charset=UTF-8");
                return "Ошибка: Неверный формат запроса";
            }
        });
        router.get("/res", (req, res) -> {
            res.addHeader("Content-Type", "text/html; charset=UTF-8");
            return "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "    <title>Simple Page</title>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <h1>Hello from /res endpoint!</h1>\n" +
                    "    <p>This is a simple HTML response from the server.</p>\n" +
                    "</body>\n" +
                    "</html>";
        });
        router.get("/api/json", (req, res) -> {
            res.addHeader("Content-Type", "application/json; charset=UTF-8");
            try {
                String json = JsonXmlExample.getJsonResponse();
                res.addHeader("Content-Length", String.valueOf(json.getBytes(StandardCharsets.UTF_8).length));
                return json;
            } catch (Exception e) {
                res.setStatusCode(500);
                return JsonXmlExample.getErrorResponse("Internal server error", 500);
            }
        });

        router.post("/api/login", (req, res) -> {
            try {
                if (!authEnabled) {
                    res.setStatusCode(403);
                    res.addHeader("Content-Type", "application/json; charset=UTF-8");
                    return getAuthDisabledResponse();
                }

                Map<String, Object> requestData = JsonParser.parse(req.getBody());
                String username = (String) requestData.get("username");
                String password = (String) requestData.get("password");

                Map<String, Object> response = new HashMap<>();
                if ("admin".equals(username) && "123321".equals(password)) {
                    response.put("status", "success");
                    response.put("token", "sample-jwt-token");
                    response.put("user", Map.of(
                            "id", 1,
                            "name", "Admin",
                            "role", "admin"
                    ));
                    res.addHeader("Set-Cookie", "auth_token=sample-jwt-token; Path=/; HttpOnly");
                } else {
                    response.put("status", "error");
                    response.put("message", "Invalid credentials");
                    res.setStatusCode(401);
                }
                res.addHeader("Content-Type", "application/json; charset=UTF-8");
                return JsonBuilder.build(response);
            } catch (Exception e) {
                res.setStatusCode(400);
                res.addHeader("Content-Type", "application/json; charset=UTF-8");
                return JsonXmlExample.getErrorResponse("Invalid request", 400);
            }
        });

        router.get("/api/protected", (req, res) -> {
            if (!authEnabled) {
                res.setStatusCode(403);
                res.addHeader("Content-Type", "application/json; charset=UTF-8");
                return getAuthDisabledResponse();
            }

            String authToken = getAuthTokenFromRequest(req);
            if (!"sample-jwt-token".equals(authToken)) {
                res.setStatusCode(401);
                res.addHeader("Content-Type", "application/json; charset=UTF-8");
                return JsonXmlExample.getErrorResponse("Unauthorized", 401);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Welcome to protected area!");
            res.addHeader("Content-Type", "application/json; charset=UTF-8");
            return JsonBuilder.build(response);
        });

        new Server(router).initserver();
    }

    private static String getAuthDisabledResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("code", 403);
        response.put("message", "Authentication is currently disabled");
        response.put("timestamp", System.currentTimeMillis());
        return JsonBuilder.build(response);
    }

    private static void createDefaultFilesIfNotExist(Path staticPath) {
        try {
            if (!Files.exists(staticPath)) {
                Files.createDirectories(staticPath);
            }
            if (!Files.exists(staticPath.resolve("index.html"))) {
                Files.writeString(staticPath.resolve("index.html"), "<!DOCTYPE html><html><head><title>Server</title></head><body><h1>Welcome</h1></body></html>");
            }
        } catch (IOException e) {
            System.err.println("Failed to create default files: " + e.getMessage());
        }
    }

    private static String getAuthTokenFromRequest(HttpRequest req) {
        String cookieHeader = req.getHeaders().getOrDefault("Cookie", "");
        return Arrays.stream(cookieHeader.split(";"))
                .map(String::trim)
                .filter(c -> c.startsWith("auth_token="))
                .map(c -> c.substring("auth_token=".length()))
                .findFirst()
                .orElse("");
    }
}