package com.mindplates.nextchapter.adapter.out.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 벤더 응답을 흉내내는 로컬 HTTP 서버.
 *
 * <p>{@code RestClient} 를 목으로 바꿔치기하지 않고 실제 HTTP 를 태우는 이유는, 이 어댑터에서
 * 틀릴 수 있는 것이 <b>헤더 이름과 JSON 필드 이름</b>이기 때문이다. 클라이언트를 목으로 바꾸면 그
 * 두 가지가 정확히 테스트에서 빠진다 — {@code input_tokens} 를 {@code inputTokens} 로 읽고 있어도
 * 통과한다.
 */
final class StubAiServer implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, String> receivedHeaders = new HashMap<>();
    private String receivedBody;

    private StubAiServer(HttpServer server) {
        this.server = server;
    }

    static StubAiServer respondingWith(String path, int status, String jsonBody) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        StubAiServer stub = new StubAiServer(httpServer);
        httpServer.createContext(path, exchange -> stub.handle(exchange, status, jsonBody));
        httpServer.start();
        return stub;
    }

    private void handle(HttpExchange exchange, int status, String jsonBody) throws IOException {
        exchange.getRequestHeaders().forEach((name, values) -> receivedHeaders.put(name, String.join(",", values)));
        try (InputStream in = exchange.getRequestBody()) {
            receivedBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        byte[] payload = jsonBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    String header(String name) {
        return receivedHeaders.get(name);
    }

    String body() {
        return receivedBody;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
