package com.mindplates.nextchapter.domain.chapter.model;

import java.util.List;

/**
 * 블록 타입. 렌더러가 형태별로 해석한다 — 웹은 HTML, 영상은 {@link #NARRATION} 을 TTS·자막으로,
 * PPT 는 슬라이드로.
 *
 * <p>특정 형태에서만 쓰이는 블록({@link #NARRATION})도 같은 문서 안에 둔다. 형태별로 문서를 나누는
 * 순간 소스가 갈라지고, 그러면 여러 사람의 반응을 겹쳐 볼 수 없다.
 */
public enum BlockType {
    HEADING(true, List.of()),
    PARAGRAPH(true, List.of()),

    /** 도표·그래프. 렌더 방법은 {@code spec} 이 담는다 — 이미지 바이너리가 아니라 명세다. */
    FIGURE(false, List.of("spec")),

    /** 수식. {@code text} 는 LaTeX 소스다. */
    FORMULA(true, List.of()),

    /** 음성·자막의 원천. 웹 렌더러는 이 블록을 건너뛴다. */
    NARRATION(true, List.of()),

    /**
     * 퀴즈. 정답이 없는 퀴즈는 신호를 만들 수 없으므로 필수 속성으로 강제한다 — 오답률이 집단
     * 루프의 핵심 신호이고, 채점할 수 없는 문항은 그 신호를 만들지 못한다.
     */
    QUIZ(false, List.of("question", "choices", "answer"));

    private final boolean textRequired;
    private final List<String> requiredAttributes;

    BlockType(boolean textRequired, List<String> requiredAttributes) {
        this.textRequired = textRequired;
        this.requiredAttributes = requiredAttributes;
    }

    public boolean isTextRequired() {
        return textRequired;
    }

    public List<String> requiredAttributes() {
        return requiredAttributes;
    }

    /** 이 타입이 특정 딜리버리 형태에서만 쓰이는지. 웹 렌더러가 건너뛸 블록을 판정한다. */
    public boolean isDeliverySpecific() {
        return this == NARRATION;
    }
}
