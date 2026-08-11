package com.mindplates.nextchapter.domain.catalog.model;

/**
 * 어떻게 수렴했는지. 매핑 품질을 사후에 볼 수 있어야 컷오프와 프롬프트를 조정할 수 있다.
 *
 * <p>특히 {@link #ALIAS} 와 {@link #LLM} 의 비율이 중요하다 — 별칭으로 처리되는 비율이 높아지면
 * 그만큼 모델 호출이 줄어든다는 뜻이고, 반복 입력을 별칭으로 승격시키는 것이 가장 싼 최적화다.
 */
public enum TopicMatchSource {
    /** 별칭·주제명 정확 일치. 모델을 부르지 않았다. */
    ALIAS,
    /** LLM 이 단계적 매핑으로 찾았다. */
    LLM,
    /** 후보 중에서 사용자가 골랐다. */
    USER_CONFIRMED,
    /** 사용자가 전부 거절해 새로 만들었다. */
    NEW_TOPIC
}
