package com.mindplates.nextchapter.adapter.security.social;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 제공자 응답을 흉내내는 로컬 HTTP 서버.
 *
 * <p>{@code RestClient} 를 목으로 바꿔치기하지 않고 실제 HTTP 를 태우는 이유는, 이 어댑터에서 틀릴 수
 * 있는 것이 <b>요청 형식과 JSON 필드 이름</b>이기 때문이다. 클라이언트를 목으로 바꾸면 그 둘이 정확히
 * 테스트에서 빠진다 — {@code kakao_account.email} 을 {@code email} 로 읽고 있어도 통과한다.
 */
final class StubSocialServer implements AutoCloseable {

    static final String TOKEN_PATH = "/token";
    static final String USER_INFO_PATH = "/userinfo";

    private final HttpServer server;
    private final Map<String, String> receivedHeaders = new HashMap<>();
    private String receivedForm;

    private StubSocialServer(HttpServer server) {
        this.server = server;
    }

    static StubSocialServer with(String tokenBody, String userInfoBody) throws IOException {
        return with(tokenBody, 200, userInfoBody, 200);
    }

    static StubSocialServer with(String tokenBody, int tokenStatus, String userInfoBody, int userInfoStatus)
            throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        StubSocialServer stub = new StubSocialServer(httpServer);
        httpServer.createContext(TOKEN_PATH, exchange -> stub.handle(exchange, tokenStatus, tokenBody, true));
        httpServer.createContext(
                USER_INFO_PATH, exchange -> stub.handle(exchange, userInfoStatus, userInfoBody, false));
        httpServer.start();
        return stub;
    }

    private void handle(HttpExchange exchange, int status, String body, boolean captureForm) throws IOException {
        exchange.getRequestHeaders().forEach((name, values) -> receivedHeaders.put(name, String.join(",", values)));
        try (InputStream in = exchange.getRequestBody()) {
            String read = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (captureForm) {
                receivedForm = read;
            }
        }
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    SocialAuthProperties.Registration registration(String clientId, String clientSecret) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new SocialAuthProperties.Registration(
                clientId,
                clientSecret,
                baseUrl + TOKEN_PATH,
                baseUrl + USER_INFO_PATH,
                baseUrl + "/authorize",
                "profile email");
    }

    /** 토큰 교환 요청의 폼 본문. 제공자가 대조하는 값들이 실제로 실려 나갔는지 확인한다. */
    String tokenForm() {
        return receivedForm;
    }

    String header(String name) {
        return receivedHeaders.get(name);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
