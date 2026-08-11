package com.mindplates.nextchapter.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mindplates.nextchapter.application.generation.port.out.EmbeddingClientPort;
import com.mindplates.nextchapter.application.generation.port.out.EmbeddingRequest;
import com.mindplates.nextchapter.application.generation.port.out.EmbeddingResult;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Voyage AI 임베딩 어댑터.
 *
 * <p>임베딩만 별도 벤더인 이유는 Anthropic 이 임베딩 API 를 제공하지 않기 때문이다. 이 어댑터가
 * 프로바이더 추상화의 실증도 겸한다 — 벤더를 추가하는 비용이 "포트 구현 하나"라는 것을 두 번째
 * 구현으로 확인한다.
 *
 * <p>응답 순서를 신뢰하지 않고 {@code index} 로 정렬한다. 순서가 뒤바뀌면 주제 A 의 벡터가 주제 B 에
 * 붙고, 그건 <b>에러 없이</b> 잘못된 주제 매핑을 만든다 — 신호를 엉뚱한 뼈대에 붙이는 실패다.
 */
@Component
public class VoyageEmbeddingClient implements EmbeddingClientPort {

    private static final Logger log = LoggerFactory.getLogger(VoyageEmbeddingClient.class);

    private final RestClient restClient;

    public VoyageEmbeddingClient(AiProviderProperties properties) {
        this.restClient = AiRestClients.create(properties.voyageBaseUrl(), properties);
    }

    @Override
    public AiVendor vendor() {
        return AiVendor.VOYAGE;
    }

    @Override
    public EmbeddingResult embed(EmbeddingRequest request) {
        if (request.inputs() == null || request.inputs().isEmpty()) {
            throw new IllegalArgumentException("임베딩할 입력이 없습니다.");
        }
        try {
            EmbeddingsResponse response = restClient
                    .post()
                    .uri("/v1/embeddings")
                    .header("Authorization", "Bearer " + request.apiKey())
                    .header("content-type", "application/json")
                    .body(Map.of("model", request.model(), "input", request.inputs()))
                    .retrieve()
                    .body(EmbeddingsResponse.class);
            return toResult(request.model(), response);
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AI] Voyage 임베딩 호출 실패 model={}: {}", request.model(), e.getMessage());
            throw new ExternalApiException(
                    "Voyage 임베딩 호출에 실패했습니다: " + e.getClass().getSimpleName(), e);
        }
    }

    private static EmbeddingResult toResult(String model, EmbeddingsResponse response) {
        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new ExternalApiException("Voyage 응답에 임베딩이 없습니다.");
        }
        List<float[]> vectors = response.data().stream()
                .sorted(Comparator.comparingInt(EmbeddingData::index))
                .map(EmbeddingData::toVector)
                .toList();
        int inputTokens = response.usage() == null ? 0 : response.usage().totalTokens();
        return new EmbeddingResult(model, vectors.get(0).length, vectors, inputTokens);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingsResponse(List<EmbeddingData> data, Usage usage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingData(int index, List<Double> embedding) {

        float[] toVector() {
            if (embedding == null || embedding.isEmpty()) {
                throw new ExternalApiException("Voyage 응답의 벡터가 비어 있습니다. index=" + index);
            }
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = embedding.get(i).floatValue();
            }
            return vector;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(@JsonProperty("total_tokens") int totalTokens) {}
}
