package com.mindplates.nextchapter.domain.catalog.model;

/** 자유 입력 하나가 도달한 결말. */
public enum TopicInputResolution {
    /** 기존 주제로 수렴했다. 뼈대에 합류한다. */
    MATCHED,
    /** 후보를 제시하고 사용자의 판정을 기다리는 중이다. */
    CANDIDATES_PRESENTED,
    /** 사용자가 후보를 모두 거절해 신규 주제를 등록했다. */
    NEW_TOPIC_CREATED,
    /**
     * 붙일 분야가 없어 운영자 검토로 넘어갔다.
     *
     * <p>신규 도메인·분야를 자동으로 만들지 않기 때문에 생기는 상태다. 층별 좁히기가 성립하려면
     * 상위 계층이 사람의 판단으로만 늘어야 한다 — 자동 생성하면 카탈로그가 입력만큼 넓어지고
     * 매핑 후보가 다시 수백 개가 된다.
     */
    NEEDS_REVIEW
}
