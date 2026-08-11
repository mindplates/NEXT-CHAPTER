package com.mindplates.nextchapter.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionRequest;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionResult;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Anthropic 완성 어댑터")
class AnthropicLlmClientTest {

    private static final String SUCCESS_BODY =
            """
            {
              "id": "msg_1",
              "type": "message",
              "role": "assistant",
              "content": [
                {"type": "text", "text": "경사하강법은 "},
                {"type": "text", "text": "손실을 줄이는 방향으로 파라미터를 옮긴다."}
              ],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 120, "output_tokens": 45}
            }
            """;

    @Test
    @DisplayName("텍스트 블록을 이어 붙이고 토큰 사용량을 읽는다")
    void parsesTextAndUsage() throws IOException {
        try (StubAiServer stub = StubAiServer.respondingWith("/v1/messages", 200, SUCCESS_BODY)) {
            LlmCompletionResult result = clientFor(stub).complete(request());

            assertThat(result.text()).isEqualTo("경사하강법은 손실을 줄이는 방향으로 파라미터를 옮긴다.");
            assertThat(result.inputTokens()).isEqualTo(120);
            assertThat(result.outputTokens()).isEqualTo(45);
            assertThat(result.totalTokens()).isEqualTo(165);
        }
    }

    @Test
    @DisplayName("요청 헤더에 x-api-key 와 anthropic-version 을 싣는다")
    void sendsRequiredHeaders() throws IOException {
        try (StubAiServer stub = StubAiServer.respondingWith("/v1/messages", 200, SUCCESS_BODY)) {
            clientFor(stub).complete(request());

            assertThat(stub.header("X-api-key")).isEqualTo("sk-test-key");
            assertThat(stub.header("Anthropic-version")).isEqualTo("2023-06-01");
        }
    }

    @Test
    @DisplayName("요청 본문의 모델은 호출자가 넘긴 값이다 — 어댑터가 모델을 고르지 않는다")
    void usesGivenModel() throws IOException {
        try (StubAiServer stub = StubAiServer.respondingWith("/v1/messages", 200, SUCCESS_BODY)) {
            clientFor(stub).complete(request());

            assertThat(stub.body()).contains("\"model\":\"claude-opus-5\"");
            assertThat(stub.body()).contains("\"system\":\"너는 교육 콘텐츠 작성자다.\"");
        }
    }

    @Test
    @DisplayName("실패 응답은 ExternalApiException 이고, 메시지에 원본 응답을 싣지 않는다")
    void wrapsFailures() throws IOException {
        String errorBody = "{\"error\":{\"message\":\"invalid x-api-key\"}}";
        try (StubAiServer stub = StubAiServer.respondingWith("/v1/messages", 401, errorBody)) {
            AnthropicLlmClient client = clientFor(stub);

            assertThatThrownBy(() -> client.complete(request()))
                    .isInstanceOf(ExternalApiException.class)
                    .hasMessageNotContaining("invalid x-api-key")
                    .hasMessageNotContaining("sk-test-key");
        }
    }

    @Test
    @DisplayName("콘텐츠가 비어 있으면 실패로 다룬다 — 빈 본문이 챕터로 저장되면 안 된다")
    void rejectsEmptyContent() throws IOException {
        try (StubAiServer stub = StubAiServer.respondingWith("/v1/messages", 200, "{\"content\":[]}")) {
            AnthropicLlmClient client = clientFor(stub);

            assertThatThrownBy(() -> client.complete(request())).isInstanceOf(ExternalApiException.class);
        }
    }

    @Test
    @DisplayName("벤더를 스스로 알려 준다 — 레지스트리가 클래스 이름에 의존하지 않게")
    void reportsVendor() {
        assertThat(clientFor("http://localhost:1").vendor()).isEqualTo(AiVendor.ANTHROPIC);
    }

    private static AnthropicLlmClient clientFor(StubAiServer stub) {
        return clientFor(stub.baseUrl());
    }

    private static AnthropicLlmClient clientFor(String baseUrl) {
        return new AnthropicLlmClient(
                new AiProviderProperties(baseUrl, "2023-06-01", null, Duration.ofSeconds(2), Duration.ofSeconds(5)));
    }

    private static LlmCompletionRequest request() {
        return new LlmCompletionRequest("claude-opus-5", "sk-test-key", "너는 교육 콘텐츠 작성자다.", "경사하강법을 설명해라.", 1024, null);
    }
}
