package com.mindplates.nextchapter.domain.layer.model;

/**
 * 왜 이 챕터를 다음으로 골랐는가.
 *
 * <p>이유를 함께 내리는 이유는 <b>사용자가 납득할 수 있어야</b> 하기 때문이다. 순서만 주면 "왜 앞으로 안
 * 가고 뒤로 가나"에 답할 수 없고, 그러면 사용자는 추천을 무시하고 목차처럼 훑는다 — 그 순간 그래프로 둔
 * 이유가 사라진다.
 */
public enum RecommendationReason {

    /** 막혀 있어 선행 챕터로 되돌린다. 앞으로 밀어도 같은 지점에서 다시 막힌다. */
    PREREQUISITE_GAP,

    /** 이 챕터를 선행으로 두는 다음 챕터. */
    NEXT_STEP,

    /** 충분히 이해했으니 심화로. */
    DEEPEN,

    /** 순서는 없지만 함께 보면 도움이 되는 것. */
    RELATED,

    /** 이미 끝까지 본 챕터. 다른 후보가 없을 때만 제시한다. */
    REVIEW
}
