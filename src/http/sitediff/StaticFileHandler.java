package http.sitediff;

import http.httpdiff.HttpHandler;
import http.httpdiff.HttpRequest;
import http.httpdiff.HttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StaticFileHandler implements HttpHandler {
    private final Path basePath;

    public StaticFileHandler(String basePath) {
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
        System.out.println("Serving static files from: " + this.basePath);
    }

    @Override
    public String handle(HttpRequest request, HttpResponse response) throws IOException {
        String requestPath = request.getUrl().split("\\?")[0];
        String path = requestPath.equals("/") ? "/index.html" : requestPath;

        Path filePath = basePath.resolve(path.substring(1)).normalize();

        if (!filePath.startsWith(basePath)) {
            response.setStatusCode(403);
            return "Forbidden";
        }

        if (!Files.exists(filePath)) {
            response.setStatusCode(404);
            return null;
        }

        if (Files.isDirectory(filePath)) {
            filePath = filePath.resolve("index.html");
            if (!Files.exists(filePath)) {
                response.setStatusCode(403);
                return "Directory listing not allowed";
            }
        }

        String contentType = getContentType(filePath);
        response.addHeader("Content-Type", contentType);
        response.addHeader("Charset", "UTF-8");

        byte[] fileBytes = Files.readAllBytes(filePath);
        return new String(fileBytes, StandardCharsets.UTF_8);
    }

    private String getContentType(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".html")) return ContentType.TEXT_HTML_UTF8;
        if (fileName.endsWith(".css")) return ContentType.TEXT_CSS;
        if (fileName.endsWith(".js")) return ContentType.TEXT_JAVASCRIPT;
        if (fileName.endsWith(".json")) return ContentType.APPLICATION_JSON_UTF8;
        if (fileName.endsWith(".xml")) return ContentType.APPLICATION_XML_UTF8;
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return ContentType.IMAGE_JPEG;
        if (fileName.endsWith(".png")) return ContentType.IMAGE_PNG;
        return ContentType.TEXT_PLAIN;
    }
}