package http.sitediff;

import http.httpdiff.HttpHandler;
import http.httpdiff.HttpHeader;
import http.httpdiff.HttpRequest;
import http.httpdiff.HttpResponse;
import http.middleware.Middleware;
import json.JsonXmlExample;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Router implements HttpHandler {
    private final Map<String, HttpHandler> routes = new ConcurrentHashMap<>();
    private final List<Middleware> middlewares = new ArrayList<>();
    private StaticFileHandler staticFileHandler;

    public void get(String path, HttpHandler handler) {
        routes.put("GET " + normalizePath(path), handler);
    }

    public void post(String path, HttpHandler handler) {
        routes.put("POST " + normalizePath(path), handler);
    }

    public void use(Middleware middleware) {
        middlewares.add(middleware);
    }

    public void setStaticFileHandler(StaticFileHandler handler) {
        this.staticFileHandler = handler;
    }

    @Override
    public String handle(HttpRequest request, HttpResponse response) throws IOException {
        for (Middleware middleware : middlewares) {
            if (!middleware.handle(request, response)) {
                return null;
            }
        }

        String path = normalizePath(request.getUrl().split("\\?")[0]);
        String method = String.valueOf(request.getMethod());
        String key = method + " " + path;

        HttpHandler handler = routes.get(key);
        if (handler != null) {
            return handler.handle(request, response);
        }

        if (staticFileHandler != null && method.equals("GET")) {
            try {
                String result = staticFileHandler.handle(request, response);
                if (response.getStatusCode() != 404) {
                    return result;
                }
            } catch (IOException e) {
                response.setStatusCode(500);
                return JsonXmlExample.getErrorResponse("Internal server error", 500);
            }
        }
        response.setStatusCode(404);
        response.addHeader(HttpHeader.CONTENT_TYPE, ContentType.APPLICATION_JSON_UTF8);
        return JsonXmlExample.getErrorResponse("Resource not found at path: " + path, 404);
    }

    private String normalizePath(String path) {
        return "/" + path.replaceAll("^/+|/+$", "");
    }
}