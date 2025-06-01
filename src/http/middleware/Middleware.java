package http.middleware;

import http.httpdiff.HttpRequest;
import http.httpdiff.HttpResponse;

public interface Middleware {
    boolean handle(HttpRequest request, HttpResponse response);
}