package com.mindplates.nextchapter.domain.catalog.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 자유 입력 하나의 처리 기록. 대화 상태와 검토 대기열을 동시에 맡는다.
 *
 * <p>후보를 제시한 뒤 사용자의 응답을 기다릴 때 이 행의 ID 가 요청 토큰이 된다. 별도 세션 저장소를
 * 두지 않는 이유는 이 상태가 어차피 남겨야 하는 기록이기 때문이다 — "사용자가 후보를 모두 거절했다"는
 * 사실이 신규 뼈대가 만들어진 유일한 근거다.
 *
 * @param proposedFieldId LLM 이 고른 분야. null 이면 붙일 자리가 없다는 뜻이고, 그 입력은 운영자
 *     검토로 간다 — 신규 도메인·분야는 자동으로 만들지 않는다
 * @param proposedTopicSlug LLM 이 제안한 slug. 한글 주제명에서는 ASCII slug 를 만들 수 없으므로
 *     모델이 제안한 값을 남겨 둔다. 없으면 거절 경로가 입력 로그 ID 로 폴백한다
 */
public record TopicInputLog(
        Long id,
        String rawInput,
        String normalized,
        TopicInputResolution resolution,
        TopicMatchSource matchSource,
        Long matchedTopicId,
        Long proposedFieldId,
        String proposedTopicName,
        String proposedTopicSlug,
        List<Long> candidateTopicIds,
        String userId,
        LocalDateTime reviewedAt,
        String reviewedBy,
        String reviewNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public TopicInputLog {
        rawInput = Strings.requireMaxLength(Strings.requireText(rawInput, "입력"), 500, "입력");
        normalized = Strings.normalizeForMatch(rawInput);
        if (resolution == null) {
            throw new IllegalArgumentException("처리 결과는 필수입니다.");
        }
        proposedTopicName = Strings.trimToNull(proposedTopicName);
        proposedTopicSlug = Strings.trimToNull(proposedTopicSlug);
        candidateTopicIds = candidateTopicIds == null ? List.of() : List.copyOf(candidateTopicIds);
    }

    public static TopicInputLog matched(String rawInput, Long topicId, TopicMatchSource source, String userId) {
        return new TopicInputLog(
                null,
                rawInput,
                null,
                TopicInputResolution.MATCHED,
                source,
                topicId,
                null,
                null,
                null,
                List.of(),
                userId,
                null,
                null,
                null,
                null,
                null);
    }

    public static TopicInputLog candidatesPresented(
            String rawInput,
            List<Long> candidateTopicIds,
            Long proposedFieldId,
            String proposedTopicName,
            String proposedTopicSlug,
            String userId) {
        return new TopicInputLog(
                null,
                rawInput,
                null,
                TopicInputResolution.CANDIDATES_PRESENTED,
                null,
                null,
                proposedFieldId,
                proposedTopicName,
                proposedTopicSlug,
                candidateTopicIds,
                userId,
                null,
                null,
                null,
                null,
                null);
    }

    public static TopicInputLog needsReview(
            String rawInput, String proposedTopicName, String proposedTopicSlug, String userId) {
        return new TopicInputLog(
                null,
                rawInput,
                null,
                TopicInputResolution.NEEDS_REVIEW,
                null,
                null,
                null,
                proposedTopicName,
                proposedTopicSlug,
                List.of(),
                userId,
                null,
                null,
                null,
                null,
                null);
    }

    /** 후보 중 하나를 사용자가 골랐다. */
    public TopicInputLog resolvedByUser(Long topicId) {
        return withOutcome(TopicInputResolution.MATCHED, TopicMatchSource.USER_CONFIRMED, topicId);
    }

    /** 사용자가 전부 거절해 신규 주제를 만들었다. */
    public TopicInputLog resolvedByNewTopic(Long topicId) {
        return withOutcome(TopicInputResolution.NEW_TOPIC_CREATED, TopicMatchSource.NEW_TOPIC, topicId);
    }

    /** 붙일 분야가 없어 검토로 넘긴다. */
    public TopicInputLog escalatedToReview() {
        return withOutcome(TopicInputResolution.NEEDS_REVIEW, null, null);
    }

    public TopicInputLog reviewed(String reviewer, String note) {
        return new TopicInputLog(
                id,
                rawInput,
                normalized,
                resolution,
                matchSource,
                matchedTopicId,
                proposedFieldId,
                proposedTopicName,
                proposedTopicSlug,
                candidateTopicIds,
                userId,
                LocalDateTime.now(),
                reviewer,
                note,
                createdAt,
                updatedAt);
    }

    /** 후보 제시 상태에서만 사용자의 판정을 받을 수 있다. 같은 토큰을 두 번 쓰는 것을 막는다. */
    public boolean awaitsUserDecision() {
        return resolution == TopicInputResolution.CANDIDATES_PRESENTED;
    }

    public boolean isReviewed() {
        return reviewedAt != null;
    }

    private TopicInputLog withOutcome(
            TopicInputResolution newResolution, TopicMatchSource newSource, Long newMatchedTopicId) {
        return new TopicInputLog(
                id,
                rawInput,
                normalized,
                newResolution,
                newSource,
                newMatchedTopicId,
                proposedFieldId,
                proposedTopicName,
                proposedTopicSlug,
                candidateTopicIds,
                userId,
                reviewedAt,
                reviewedBy,
                reviewNote,
                createdAt,
                updatedAt);
    }
}
