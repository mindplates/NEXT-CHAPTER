package com.mindplates.nextchapter.domain.admin.model;

/**
 * AI 벤더. 특정 벤더에 종속되지 않기 위한 축이다.
 *
 * <p>여기 값이 있다는 것과 호출할 수 있다는 것은 다르다. 어댑터가 등록된 벤더만 실제로 쓸 수 있고,
 * 없는 벤더를 단계에 지정하려 하면 저장 시점에 거절된다 — 저장은 되고 생성 잡이 몇 시간 뒤에
 * 실패하는 쪽이 훨씬 나쁘다.
 */
public enum AiVendor {
    ANTHROPIC,
    /** 임베딩 전용. Anthropic 이 임베딩 API 를 제공하지 않아 필요하다. */
    VOYAGE,
    OPENAI
}
