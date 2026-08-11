package com.mindplates.nextchapter.domain.admin.model;

/**
 * AI 호출 단계. 단계마다 벤더와 모델을 독립적으로 설정한다.
 *
 * <p>단계를 나누는 이유는 요구가 다르기 때문이다 — 본문 생성은 품질이, 주제 매핑은 지연과 단가가
 * 지배한다. 한 모델로 묶으면 둘 중 하나가 항상 과하거나 부족하다.
 */
public enum AiStage {
    /** 뼈대 본문 생성 — 블록 문서를 만든다. */
    SKELETON_BODY,

    /** 주제 매핑·분류 — 자유 입력을 도메인 → 분야 → 주제로 좁힌다. */
    TOPIC_MAPPING,

    /** 검증 패스 — 주장과 출처의 일치를 검사한다. */
    VERIFICATION,

    /** 개인화 보충 블록 — 막힌 지점에 설명·예시를 덧붙인다. */
    PERSONALIZATION,

    /** 임베딩 — 주제·챕터 벡터를 만든다. 완성(completion) 단계가 아니다. */
    EMBEDDING;

    public boolean isEmbedding() {
        return this == EMBEDDING;
    }
}
