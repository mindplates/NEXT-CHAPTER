package com.mindplates.nextchapter.application.catalog.view;

import com.mindplates.nextchapter.domain.catalog.model.TopicInputResolution;
import com.mindplates.nextchapter.domain.catalog.model.TopicMatchSource;
import java.util.List;

/**
 * 자유 입력 처리 결과.
 *
 * <p>{@code requestId} 가 대화의 연결 고리다. 후보를 제시한 뒤 사용자의 판정을 받으려면 "무엇에 대한
 * 판정인가"가 서버에 남아 있어야 하고, 그 값이 입력 로그 행의 ID 다.
 *
 * @param resolution 결말. {@code CANDIDATES_PRESENTED} 면 사용자의 판정이 필요하다
 * @param canCreateNew 후보를 모두 거절했을 때 신규 주제를 만들 수 있는지. LLM 이 붙일 분야를 찾지
 *     못했으면 false 이고, 그 입력은 운영자 검토로 간다 — 신규 도메인·분야는 자동으로 만들지 않는다
 */
public record TopicResolutionView(
        Long requestId,
        TopicInputResolution resolution,
        TopicMatchSource matchSource,
        Long topicId,
        String topicName,
        String topicSlug,
        String fieldName,
        String domainName,
        boolean skeletonExists,
        List<TopicSimilarityView> candidates,
        boolean canCreateNew,
        String proposedTopicName) {}
