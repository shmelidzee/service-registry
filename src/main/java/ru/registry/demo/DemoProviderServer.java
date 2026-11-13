package ru.registry.demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ru.registry.client.ProviderClientFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class DemoProviderServer implements AutoCloseable {

    static final String AVAILABLE_SUFFIX = "0001";
    static final String FAILING_SUFFIX = "0500";

    private final HttpServer server;
    private final Map<String, String> apiKeys;

    private DemoProviderServer(HttpServer server, Map<String, String> apiKeys) {
        this.server = server;
        this.apiKeys = Map.copyOf(apiKeys);
    }

    static DemoProviderServer start(Map<String, String> apiKeys) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        DemoProviderServer demo = new DemoProviderServer(server, apiKeys);
        server.createContext("/", demo::handle);
        server.start();
        return demo;
    }

    String baseUrl(String provider) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + provider;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String[] segments = exchange.getRequestURI().getPath().split("/");
            if (segments.length != 4 || !"subscribers".equals(segments[2])) {
                respond(exchange, 400, "{\"error\":\"unknown path\"}");
                return;
            }
            String expectedKey = apiKeys.get(segments[1]);
            String actualKey = exchange.getRequestHeaders().getFirst(ProviderClientFactory.DEFAULT_API_KEY_HEADER);
            if (expectedKey == null || !expectedKey.equals(actualKey)) {
                respond(exchange, 401, "{\"error\":\"invalid api key\"}");
                return;
            }
            String phone = segments[3];
            if (phone.endsWith(AVAILABLE_SUFFIX)) {
                respond(exchange, 200, "{\"status\":\"ok\"}");
            } else if (phone.endsWith(FAILING_SUFFIX)) {
                respond(exchange, 500, "{\"error\":\"provider failure\"}");
            } else {
                respond(exchange, 404, "");
            }
        } finally {
            exchange.close();
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }
}
