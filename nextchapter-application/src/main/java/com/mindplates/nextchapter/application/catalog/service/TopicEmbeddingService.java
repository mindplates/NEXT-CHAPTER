package com.mindplates.nextchapter.application.catalog.service;

import com.mindplates.nextchapter.application.catalog.port.in.EmbedTopicUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.ReembedTopicsUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.SearchSimilarTopicsUseCase;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicAliasPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicEmbeddingPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveTopicEmbeddingPort;
import com.mindplates.nextchapter.application.catalog.view.EmbeddingStatusView;
import com.mindplates.nextchapter.application.catalog.view.TopicSimilarityView;
import com.mindplates.nextchapter.application.generation.service.AiGateway;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.text.Strings;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.catalog.model.TopicEmbedding;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주제 임베딩의 생성 · 검색 · 재계산.
 *
 * <p>모델 이름을 이 서비스가 정하지 않는다. {@link AiGateway#currentEmbeddingModel()} 이 관리 화면
 * 설정을 읽어 주고, 저장·검색·재임베딩 판정이 모두 그 값을 기준으로 한다. 그래서 "모델을 바꿨다"가
 * 곧 "기존 벡터가 전부 outdated 가 된다"로 이어진다 — 별도 무효화 작업이 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TopicEmbeddingService implements EmbedTopicUseCase, SearchSimilarTopicsUseCase, ReembedTopicsUseCase {

    private static final Logger log = LoggerFactory.getLogger(TopicEmbeddingService.class);

    /** 후보 제시 개수의 기본값. 너무 많으면 사용자가 고르지 못하고, 너무 적으면 통합 기회를 놓친다. */
    private static final int DEFAULT_SEARCH_LIMIT = 5;

    /**
     * 유사도 컷오프. 낮으면 오매칭, 높으면 불필요한 신규 뼈대가 늘어난다.
     *
     * <p>실측 없이 정한 초기값이다 — 이 값이 신규 뼈대 생성 비용을 직접 좌우하므로 M1 이 끝난 뒤
     * 실제 입력으로 조정한다. 지금 상수로 두고, 조정이 필요해지는 시점에 설정값으로 뺀다.
     */
    private static final double MIN_SIMILARITY = 0.55;

    private final LoadCatalogTopicPort loadCatalogTopicPort;
    private final LoadTopicAliasPort loadTopicAliasPort;
    private final LoadTopicEmbeddingPort loadTopicEmbeddingPort;
    private final SaveTopicEmbeddingPort saveTopicEmbeddingPort;
    private final AiGateway aiGateway;

    @Override
    public TopicEmbedding embed(Long topicId) {
        CatalogTopic topic =
                loadCatalogTopicPort.findById(topicId).orElseThrow(() -> new EntityNotFoundException("주제", topicId));
        String sourceText = TopicEmbedding.sourceTextOf(topic, loadTopicAliasPort.findByTopicId(topicId));
        var result = aiGateway.embed(List.of(sourceText));

        return saveTopicEmbeddingPort.save(TopicEmbedding.of(topicId, result.model(), sourceText, result.first()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicSimilarityView> search(String freeText, int limit) {
        String text = Strings.requireText(freeText, "검색어");
        String model = aiGateway.currentEmbeddingModel();
        var result = aiGateway.embed(List.of(text));

        return loadTopicEmbeddingPort.searchSimilar(
                result.first(), model, limit <= 0 ? DEFAULT_SEARCH_LIMIT : limit, MIN_SIMILARITY);
    }

    @Override
    @Transactional(readOnly = true)
    public EmbeddingStatusView status() {
        String model = aiGateway.currentEmbeddingModel();
        return new EmbeddingStatusView(
                model,
                loadTopicEmbeddingPort.countByModel(model),
                loadTopicEmbeddingPort.countTopicsNotEmbeddedWith(model));
    }

    /**
     * 실패한 주제 하나가 배치 전체를 멈추지 않게 한다. 한 주제의 텍스트가 벤더 제한에 걸려도 나머지는
     * 갱신되어야 하고, 남은 것은 다음 호출이 다시 집는다 — 대상 목록이 "현재 모델이 아닌 행"이라
     * 성공한 것만 목록에서 빠진다.
     */
    @Override
    public EmbeddingStatusView reembedBatch(int batchSize) {
        String model = aiGateway.currentEmbeddingModel();
        List<Long> targets = loadTopicEmbeddingPort.findTopicIdsNotEmbeddedWith(model, Math.max(1, batchSize));

        int succeeded = 0;
        for (Long topicId : targets) {
            try {
                embed(topicId);
                succeeded++;
            } catch (RuntimeException e) {
                log.warn("[임베딩] 주제 {} 재임베딩 실패, 다음 배치에서 다시 시도한다: {}", topicId, e.getMessage());
            }
        }
        log.info("[임베딩] 재임베딩 배치 model={} 대상={} 성공={}", model, targets.size(), succeeded);

        return new EmbeddingStatusView(
                model,
                loadTopicEmbeddingPort.countByModel(model),
                loadTopicEmbeddingPort.countTopicsNotEmbeddedWith(model));
    }
}
