package http.httpdiff;

import java.io.IOException;

public interface HttpHandler {
    String handle(HttpRequest request, HttpResponse response) throws IOException;
}
