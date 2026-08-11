package com.mindplates.nextchapter.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.application.generation.port.out.LlmBatchClientPort;
import com.mindplates.nextchapter.application.generation.port.out.LlmBatchItem;
import com.mindplates.nextchapter.application.generation.port.out.LlmBatchItemResult;
import com.mindplates.nextchapter.application.generation.port.out.LlmBatchPollRequest;
import com.mindplates.nextchapter.application.generation.port.out.LlmBatchPollResult;
import com.mindplates.nextchapter.application.generation.port.out.LlmBatchSubmitRequest;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Anthropic Message Batches API 어댑터.
 *
 * <p>완성 어댑터와 <b>같은 요청 형식</b>을 쓴다 — 배치 항목의 {@code params} 가 Messages API 의 본문이다.
 * 그래서 프롬프트를 배치용으로 다시 만들 필요가 없고, 두 경로가 같은 본문을 생성한다.
 *
 * <p>결과는 JSON 이 아니라 <b>JSONL</b>(줄 단위 JSON)로 온다. 한 줄이 항목 하나이므로 줄 단위로 파싱하고,
 * <b>깨진 줄 하나가 나머지를 버리게 하지 않는다</b> — 배치 하나가 챕터 수십 개이므로 전부 버리면 그 비용을
 * 다시 낸다.
 */
@Component
public class AnthropicBatchClient implements LlmBatchClientPort {

    private static final Logger log = LoggerFactory.getLogger(AnthropicBatchClient.class);

    /** 벤더가 처리를 끝낸 상태. 항목별 성공·실패는 결과에서 갈린다. */
    private static final String STATUS_ENDED = "ended";

    private final RestClient restClient;
    private final String anthropicVersion;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnthropicBatchClient(AiProviderProperties properties) {
        this.restClient = AiRestClients.create(properties.anthropicBaseUrl(), properties);
        this.anthropicVersion = properties.anthropicVersion();
    }

    @Override
    public AiVendor vendor() {
        return AiVendor.ANTHROPIC;
    }

    @Override
    public String submit(LlmBatchSubmitRequest request) {
        List<Map<String, Object>> requests = request.items().stream()
                .map(item -> Map.of("custom_id", item.customId(), "params", params(request.model(), item)))
                .toList();
        try {
            BatchResponse response = restClient
                    .post()
                    .uri("/v1/messages/batches")
                    .headers(headers -> apply(headers, request.apiKey()))
                    .body(Map.of("requests", requests))
                    .retrieve()
                    .body(BatchResponse.class);
            if (response == null || response.id() == null) {
                throw new ExternalApiException("Anthropic 배치 응답에 배치 ID 가 없습니다.");
            }
            return response.id();
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            // 원인 예외를 응답 메시지에 그대로 싣지 않는다. 실패 응답 본문·헤더에 키와 프롬프트가 섞여 있다.
            log.error("[AI] Anthropic 배치 제출 실패 model={}: {}", request.model(), e.getMessage());
            throw new ExternalApiException(
                    "Anthropic 배치 제출에 실패했습니다: " + e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public LlmBatchPollResult poll(LlmBatchPollRequest request) {
        BatchResponse response = fetchStatus(request);
        if (!STATUS_ENDED.equals(response.processingStatus())) {
            return LlmBatchPollResult.inProgress();
        }
        if (response.resultsUrl() == null) {
            // 끝났다는데 결과 위치가 없다. 이 상태로 완료 처리하면 전 챕터가 조용히 빠진다.
            throw new ExternalApiException("Anthropic 배치가 끝났으나 결과 URL 이 없습니다. batchId=" + request.batchId());
        }
        return LlmBatchPollResult.ended(parseResults(fetchResults(request, response.resultsUrl())));
    }

    private BatchResponse fetchStatus(LlmBatchPollRequest request) {
        try {
            BatchResponse response = restClient
                    .get()
                    .uri("/v1/messages/batches/{batchId}", request.batchId())
                    .headers(headers -> apply(headers, request.apiKey()))
                    .retrieve()
                    .body(BatchResponse.class);
            if (response == null) {
                throw new ExternalApiException("Anthropic 배치 조회 응답이 비어 있습니다. batchId=" + request.batchId());
            }
            return response;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AI] Anthropic 배치 조회 실패 batchId={}: {}", request.batchId(), e.getMessage());
            throw new ExternalApiException(
                    "Anthropic 배치 조회에 실패했습니다: " + e.getClass().getSimpleName(), e);
        }
    }

    /**
     * 결과 URL 은 절대 주소로 온다. {@code baseUrl} 이 붙지 않도록 그대로 넘긴다 — 프록시를 끼운 환경에서
     * 벤더가 자기 도메인을 돌려주면 그쪽으로 직접 가는 것이 맞다.
     */
    private String fetchResults(LlmBatchPollRequest request, String resultsUrl) {
        try {
            return restClient
                    .get()
                    .uri(java.net.URI.create(resultsUrl))
                    .headers(headers -> apply(headers, request.apiKey()))
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("[AI] Anthropic 배치 결과 수신 실패 batchId={}: {}", request.batchId(), e.getMessage());
            throw new ExternalApiException(
                    "Anthropic 배치 결과 수신에 실패했습니다: " + e.getClass().getSimpleName(), e);
        }
    }

    /**
     * JSONL 을 줄 단위로 읽는다.
     *
     * <p><b>깨진 줄 하나가 나머지를 버리게 하지 않는다.</b> 배치 하나가 챕터 수십 개이므로 전부 버리면 그
     * 비용을 다시 낸다. 읽지 못한 줄은 어느 챕터인지도 알 수 없으므로 결과에서 빠지고, 그 챕터는 응답에
     * 오지 않은 것으로 취급돼 개별 경로로 넘어간다.
     */
    private List<LlmBatchItemResult> parseResults(String jsonl) {
        List<LlmBatchItemResult> results = new ArrayList<>();
        if (jsonl == null || jsonl.isBlank()) {
            return results;
        }
        for (String line : jsonl.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            try {
                results.add(parseLine(objectMapper.readTree(line)));
            } catch (Exception e) {
                log.error("[AI] 배치 결과 한 줄을 읽지 못했다: {}", e.getMessage());
            }
        }
        return results;
    }

    private static LlmBatchItemResult parseLine(JsonNode line) {
        String customId = line.path("custom_id").asText(null);
        JsonNode result = line.path("result");
        String type = result.path("type").asText("");
        if (!"succeeded".equals(type)) {
            // errored · canceled · expired. 어느 쪽이든 그 챕터의 본문은 없다.
            return LlmBatchItemResult.failed(
                    customId, type + ": " + result.path("error").toString());
        }
        JsonNode message = result.path("message");
        StringBuilder text = new StringBuilder();
        message.path("content").forEach(block -> {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText(""));
            }
        });
        JsonNode usage = message.path("usage");
        return LlmBatchItemResult.succeeded(
                customId,
                text.toString(),
                usage.path("input_tokens").asInt(0),
                usage.path("output_tokens").asInt(0));
    }

    private static Map<String, Object> params(String model, LlmBatchItem item) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", model);
        params.put("max_tokens", item.maxTokens());
        params.put("messages", List.of(Map.of("role", "user", "content", item.userPrompt())));
        if (item.systemPrompt() != null && !item.systemPrompt().isBlank()) {
            params.put("system", item.systemPrompt());
        }
        return params;
    }

    private void apply(org.springframework.http.HttpHeaders headers, String apiKey) {
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", anthropicVersion);
        headers.set("content-type", "application/json");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BatchResponse(
            String id,
            @JsonProperty("processing_status") String processingStatus,
            @JsonProperty("results_url") String resultsUrl) {}
}
