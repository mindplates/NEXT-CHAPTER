package com.mindplates.nextchapter.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindplates.nextchapter.application.generation.port.out.EmbeddingRequest;
import com.mindplates.nextchapter.application.generation.port.out.EmbeddingResult;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Voyage 임베딩 어댑터")
class VoyageEmbeddingClientTest {

    /** index 를 일부러 뒤집어 둔 응답. 순서를 신뢰하면 벡터가 엉뚱한 입력에 붙는다. */
    private static final String OUT_OF_ORDER_BODY =
            """
            {
              "object": "list",
              "data": [
                {"object": "embedding", "index": 1, "embedding": [0.4, 0.5, 0.6]},
                {"object": "embedding", "index": 0, "embedding": [0.1, 0.2, 0.3]}
              ],
              "model": "voyage-3-large",
              "usage": {"total_tokens": 12}
            }
            """;

    @Test
    @DisplayName("응답을 index 순으로 정렬한다 — 벡터가 다른 입력에 붙으면 에러 없이 잘못된 매핑이 된다")
    void sortsByIndex() throws IOException {
        try (StubAiServer stub = StubAiServer.respondingWith("/v1/embeddings", 200, OUT_OF_ORDER_BODY)) {
            EmbeddingResult result = clientFor(stub)
                    .embed(new EmbeddingRequest("voyage-3-large", "pa-test-key", List.of("머신러닝", "딥러닝")));

            assertThat(result.vectors()).hasSize(2);
            assertThat(result.vectors().get(0)).containsExactly(0.1f, 0.2f, 0.3f);
            assertThat(result.vectors().get(1)).containsExactly(0.4f, 0.5f, 0.6f);
        }
    }

    @Test
    @DisplayName("모델과 차원을 결과에 담는다 — 재임베딩 완료 판정의 기준이다")
    void reportsModelAndDimension() throws IOException {
        try (StubAiServer stub = StubAiServer.respondingWith("/v1/embeddings", 200, OUT_OF_ORDER_BODY)) {
            EmbeddingResult result = clientFor(stub)
                    .embed(new EmbeddingRequest("voyage-3-large", "pa-test-key", List.of("머신러닝", "딥러닝")));

            assertThat(result.model()).isEqualTo("voyage-3-large");
            assertThat(result.dimension()).isEqualTo(3);
            assertThat(result.inputTokens()).isEqualTo(12);
        }
    }

    @Test
    @DisplayName("Bearer 토큰으로 인증한다")
    void sendsBearerToken() throws IOException {
        try (StubAiServer stub = StubAiServer.respondingWith("/v1/embeddings", 200, OUT_OF_ORDER_BODY)) {
            clientFor(stub).embed(new EmbeddingRequest("voyage-3-large", "pa-test-key", List.of("머신러닝")));

            assertThat(stub.header("Authorization")).isEqualTo("Bearer pa-test-key");
        }
    }

    @Test
    @DisplayName("벤더는 VOYAGE 다 — 두 번째 구현이 프로바이더 추상화의 실증이다")
    void reportsVendor() {
        assertThat(clientFor("http://localhost:1").vendor()).isEqualTo(AiVendor.VOYAGE);
    }

    private static VoyageEmbeddingClient clientFor(StubAiServer stub) {
        return clientFor(stub.baseUrl());
    }

    private static VoyageEmbeddingClient clientFor(String baseUrl) {
        return new VoyageEmbeddingClient(
                new AiProviderProperties(null, null, baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(5)));
    }
}
