package http;

public interface HttpHandler {
    String handle(HttpRequest request, HttpResponse response);
}
