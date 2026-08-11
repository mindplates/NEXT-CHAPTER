package com.mindplates.nextchapter.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.application.generation.port.out.LlmBatchItem;
import com.mindplates.nextchapter.application.generation.port.out.LlmBatchItemResult;
import com.mindplates.nextchapter.application.generation.port.out.LlmBatchPollRequest;
import com.mindplates.nextchapter.application.generation.port.out.LlmBatchPollResult;
import com.mindplates.nextchapter.application.generation.port.out.LlmBatchSubmitRequest;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 여기서 틀릴 수 있는 것은 <b>헤더 이름·JSON 필드 이름·JSONL 파싱</b>이다. 클라이언트를 목으로 바꾸면 그
 * 셋이 정확히 테스트에서 빠진다 — {@code custom_id} 를 {@code customId} 로 읽고 있어도 통과한다.
 */
@DisplayName("Anthropic 배치 어댑터")
class AnthropicBatchClientTest {

    private static final String SUBMIT_BODY =
            """
            {"id": "msgbatch_abc", "processing_status": "in_progress"}
            """;

    @Test
    @DisplayName("제출은 배치 ID 를 돌려주고 항목마다 custom_id 를 싣는다")
    void submitsWithCustomIds() throws IOException {
        try (StubAiServer stub = StubAiServer.respondingWith("/v1/messages/batches", 200, SUBMIT_BODY)) {
            String batchId = clientFor(stub)
                    .submit(new LlmBatchSubmitRequest(
                            "claude-opus-5",
                            "plain-key",
                            List.of(
                                    new LlmBatchItem("101", "시스템", "사용자-101", 16384),
                                    new LlmBatchItem("102", "시스템", "사용자-102", 16384))));

            assertThat(batchId).isEqualTo("msgbatch_abc");
            assertThat(stub.body()).contains("\"custom_id\":\"101\"").contains("\"custom_id\":\"102\"");
            assertThat(stub.body()).contains("\"max_tokens\":16384").contains("\"model\":\"claude-opus-5\"");
            assertThat(stub.header("X-api-key")).isEqualTo("plain-key");
            assertThat(stub.header("Anthropic-version")).isEqualTo("2023-06-01");
        }
    }

    /** 배치 ID 없이 성공으로 처리하면 그 배치를 다시 찾을 방법이 없고, 챕터 수십 개가 조용히 사라진다. */
    @Test
    @DisplayName("배치 ID 가 없는 응답은 실패다")
    void rejectsResponseWithoutId() throws IOException {
        try (StubAiServer stub = StubAiServer.respondingWith("/v1/messages/batches", 200, "{}")) {
            AnthropicBatchClient client = clientFor(stub);
            LlmBatchSubmitRequest request = new LlmBatchSubmitRequest(
                    "claude-opus-5", "plain-key", List.of(new LlmBatchItem("101", "s", "u", 10)));

            assertThatThrownBy(() -> client.submit(request)).isInstanceOf(ExternalApiException.class);
        }
    }

    @Test
    @DisplayName("처리 중이면 결과를 받아 오지 않는다")
    void inProgressSkipsResults() throws IOException {
        try (StubAiServer stub = StubAiServer.respondingWith(
                "/v1/messages/batches/msgbatch_abc",
                200,
                "{\"id\":\"msgbatch_abc\",\"processing_status\":\"in_progress\"}")) {
            LlmBatchPollResult result = clientFor(stub).poll(new LlmBatchPollRequest("plain-key", "msgbatch_abc"));

            assertThat(result.ended()).isFalse();
            assertThat(result.results()).isEmpty();
        }
    }

    @Test
    @DisplayName("끝난 배치의 JSONL 을 줄 단위로 읽는다")
    void parsesJsonlResults() throws IOException {
        try (StubAiServer stub = endedBatchStub()) {
            LlmBatchPollResult result = clientFor(stub).poll(new LlmBatchPollRequest("plain-key", "msgbatch_abc"));

            assertThat(result.ended()).isTrue();
            assertThat(result.results()).hasSize(3);
            LlmBatchItemResult first = result.results().stream()
                    .filter(item -> "101".equals(item.customId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(first.succeeded()).isTrue();
            assertThat(first.text()).isEqualTo("본문 앞부분본문 뒷부분");
            assertThat(first.inputTokens()).isEqualTo(120);
            assertThat(first.outputTokens()).isEqualTo(4500);
        }
    }

    /** errored·expired 는 그 챕터의 본문이 없다는 뜻이다. 성공으로 섞이면 빈 본문이 기록된다. */
    @Test
    @DisplayName("실패·만료 항목은 실패로 표시된다")
    void marksFailedItems() throws IOException {
        try (StubAiServer stub = endedBatchStub()) {
            LlmBatchPollResult result = clientFor(stub).poll(new LlmBatchPollRequest("plain-key", "msgbatch_abc"));

            assertThat(result.results())
                    .filteredOn(item -> !item.succeeded())
                    .extracting(LlmBatchItemResult::customId)
                    .containsExactlyInAnyOrder("102", "103");
        }
    }

    /** 끝났다는데 결과 위치가 없으면 완료로 처리할 수 없다 — 그러면 전 챕터가 조용히 빠진다. */
    @Test
    @DisplayName("결과 URL 이 없는 완료 응답은 실패다")
    void rejectsEndedWithoutResultsUrl() throws IOException {
        try (StubAiServer stub = StubAiServer.respondingWith(
                "/v1/messages/batches/msgbatch_abc",
                200,
                "{\"id\":\"msgbatch_abc\",\"processing_status\":\"ended\"}")) {
            AnthropicBatchClient client = clientFor(stub);
            LlmBatchPollRequest request = new LlmBatchPollRequest("plain-key", "msgbatch_abc");

            assertThatThrownBy(() -> client.poll(request))
                    .isInstanceOf(ExternalApiException.class)
                    .hasMessageContaining("결과 URL");
        }
    }

    @Test
    @DisplayName("5xx 는 ExternalApiException 으로 올라간다")
    void mapsServerError() throws IOException {
        try (StubAiServer stub = StubAiServer.respondingWith("/v1/messages/batches", 500, "{}")) {
            AnthropicBatchClient client = clientFor(stub);
            LlmBatchSubmitRequest request = new LlmBatchSubmitRequest(
                    "claude-opus-5", "plain-key", List.of(new LlmBatchItem("101", "s", "u", 10)));

            assertThatThrownBy(() -> client.submit(request)).isInstanceOf(ExternalApiException.class);
        }
    }

    @Test
    @DisplayName("벤더는 ANTHROPIC 이다")
    void reportsVendor() throws IOException {
        try (StubAiServer stub = StubAiServer.respondingWith("/v1/messages/batches", 200, SUBMIT_BODY)) {
            assertThat(clientFor(stub).vendor()).isEqualTo(AiVendor.ANTHROPIC);
        }
    }

    /**
     * 상태 응답의 {@code results_url} 이 <b>절대 주소</b>여야 한다 — 벤더의 실제 동작이고, {@code baseUrl} 이
     * 앞에 붙으면 요청이 엉뚱한 곳으로 간다. 그 주소는 서버를 띄운 뒤에야 알므로 경로를 나중에 더한다.
     */
    private static StubAiServer endedBatchStub() throws IOException {
        StubAiServer stub = StubAiServer.routing(Map.of("/results", jsonl()));
        return stub.route(
                "/v1/messages/batches/msgbatch_abc",
                "{\"id\":\"msgbatch_abc\",\"processing_status\":\"ended\",\"results_url\":\"" + stub.baseUrl()
                        + "/results\"}");
    }

    private static String jsonl() {
        return """
            {"custom_id":"101","result":{"type":"succeeded","message":{"content":[{"type":"text","text":"본문 앞부분"},{"type":"text","text":"본문 뒷부분"}],"usage":{"input_tokens":120,"output_tokens":4500}}}}
            {"custom_id":"102","result":{"type":"errored","error":{"type":"invalid_request_error"}}}
            {"custom_id":"103","result":{"type":"expired"}}
            깨진 줄
            """;
    }

    private static AnthropicBatchClient clientFor(StubAiServer stub) {
        return new AnthropicBatchClient(new AiProviderProperties(
                stub.baseUrl(), "2023-06-01", null, Duration.ofSeconds(2), Duration.ofSeconds(5)));
    }
}
