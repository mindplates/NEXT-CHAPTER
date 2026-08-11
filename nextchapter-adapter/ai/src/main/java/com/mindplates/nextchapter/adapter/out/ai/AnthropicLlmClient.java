package com.mindplates.nextchapter.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionClientPort;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionRequest;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionResult;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Anthropic Messages API 어댑터.
 *
 * <p>모델을 상수로 갖지 않는다. 요청에 실려 오는 값을 그대로 쓴다 — 그 값의 출처는 관리 화면
 * 설정이고, 여기에 기본값을 두면 설정을 우회하는 경로가 하나 생긴다.
 *
 * <p>API 키도 갖지 않는다. 호출마다 요청에서 받으므로 관리 화면에서 키를 교체하면 다음 호출부터
 * 즉시 반영된다.
 */
@Component
public class AnthropicLlmClient implements LlmCompletionClientPort {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);

    private final RestClient restClient;
    private final String anthropicVersion;

    public AnthropicLlmClient(AiProviderProperties properties) {
        this.restClient = AiRestClients.create(properties.anthropicBaseUrl(), properties);
        this.anthropicVersion = properties.anthropicVersion();
    }

    @Override
    public AiVendor vendor() {
        return AiVendor.ANTHROPIC;
    }

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("max_tokens", request.maxTokens());
        body.put("messages", List.of(Map.of("role", "user", "content", request.userPrompt())));
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            body.put("system", request.systemPrompt());
        }
        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        }

        try {
            MessagesResponse response = restClient
                    .post()
                    .uri("/v1/messages")
                    .header("x-api-key", request.apiKey())
                    .header("anthropic-version", anthropicVersion)
                    .header("content-type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(MessagesResponse.class);
            return toResult(response);
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            // 원인 예외를 응답 메시지에 그대로 싣지 않는다. 실패 응답 본문·헤더에 키와 프롬프트가 섞여 있다.
            log.error("[AI] Anthropic 호출 실패 model={}: {}", request.model(), e.getMessage());
            throw new ExternalApiException(
                    "Anthropic 호출에 실패했습니다: " + e.getClass().getSimpleName(), e);
        }
    }

    private static LlmCompletionResult toResult(MessagesResponse response) {
        if (response == null || response.content() == null || response.content().isEmpty()) {
            throw new ExternalApiException("Anthropic 응답에 콘텐츠가 없습니다.");
        }
        String text = response.content().stream()
                .filter(block -> "text".equals(block.type()))
                .map(ContentBlock::text)
                .filter(java.util.Objects::nonNull)
                .reduce("", String::concat);
        Usage usage = response.usage() == null ? new Usage(0, 0) : response.usage();
        return new LlmCompletionResult(text, usage.inputTokens(), usage.outputTokens());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MessagesResponse(List<ContentBlock> content, Usage usage, @JsonProperty("stop_reason") String stopReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(String type, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(@JsonProperty("input_tokens") int inputTokens, @JsonProperty("output_tokens") int outputTokens) {}
}
