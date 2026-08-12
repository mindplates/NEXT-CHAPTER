package com.mindplates.nextchapter.domain.signal.model;

import java.util.List;

/**
 * 신호 종류. <b>별도 평가 UI 가 없다</b>는 원칙의 실체다 — 품질 신호는 소비 과정에서 전부 발생한다.
 *
 * <p>필수 페이로드 키를 타입이 들고 있는 이유는 {@code BlockType} 이 필수 속성을 들고 있는 이유와 같다.
 * 정답 여부가 없는 퀴즈 응답은 오답률을 만들 수 없고, 본문이 없는 질문은 "같은 지점에서 묻는다"를
 * 판정할 수 없다 — 그런 신호는 저장돼도 집계에 기여하지 않으면서 건수만 늘린다.
 */
public enum SignalType {

    /** 퀴즈 응답. {@code correct} 가 오답률의 원천이다. */
    QUIZ_ANSWER(true, List.of("correct")),

    /**
     * 질문. <b>사용자의 부족함이 아니라 콘텐츠의 결함 신고다.</b>
     *
     * <p>블록을 요구하는 이유가 여기 있다. 챕터 전체에 붙으면 "이 챕터가 어렵다"까지만 알 수 있고,
     * 어느 문단·어느 수식에서 막히는지가 나와야 수정안이 만들어진다.
     */
    QUESTION(true, List.of("text")),

    /** 명백한 사실 오류 제보. 검증 패스의 트리거다. */
    ERROR_REPORT(true, List.of("text")),

    /**
     * 소비 진행. 유일하게 블록이 없어도 되는 종류다 — 본래 챕터 단위 사실이다.
     *
     * <p>"생성된 뼈대 중 끝까지 소비된 비율"(3개월 성공 기준 3번)이 이 신호에 의존한다.
     */
    PROGRESS(false, List.of("percent"));

    private final boolean blockRequired;
    private final List<String> requiredPayloadKeys;

    SignalType(boolean blockRequired, List<String> requiredPayloadKeys) {
        this.blockRequired = blockRequired;
        this.requiredPayloadKeys = requiredPayloadKeys;
    }

    public boolean isBlockRequired() {
        return blockRequired;
    }

    public List<String> requiredPayloadKeys() {
        return requiredPayloadKeys;
    }
}
