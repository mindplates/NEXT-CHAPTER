package com.mindplates.nextchapter.domain.catalog.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDateTime;

/**
 * 주제 별칭 — 자유 입력을 주제로 수렴시키는 결정적 경로.
 *
 * <p>LLM 매핑(P1.5) 앞단에 두면 이미 판정이 끝난 표현에 모델을 부르지 않는다. 비용도 지연도 0
 * 이고 결과가 결정적이다.
 */
public record TopicAlias(Long id, Long topicId, String alias, LocalDateTime createdAt) {

    public TopicAlias {
        if (topicId == null) {
            throw new IllegalArgumentException("별칭은 주제에 속해야 합니다.");
        }
        alias = Strings.requireMaxLength(Strings.requireText(alias, "별칭"), 200, "별칭");
        if (Strings.normalizeForMatch(alias) == null) {
            throw new IllegalArgumentException("별칭에 비교할 수 있는 문자가 없습니다: " + alias);
        }
    }

    public static TopicAlias create(Long topicId, String alias) {
        return new TopicAlias(null, topicId, alias, null);
    }

    /**
     * 대조용 정규화 값. 저장 컬럼이 아니라 <b>파생값</b>으로 두는 이유는, 정규화 규칙이 바뀌었을 때
     * 읽는 쪽이 항상 현재 규칙을 쓰게 하기 위해서다. 컬럼 값을 그대로 신뢰하면 규칙 변경 이후
     * 옛 행만 옛 규칙으로 남아 조용히 빗나간다.
     */
    public String normalized() {
        return Strings.normalizeForMatch(alias);
    }
}
