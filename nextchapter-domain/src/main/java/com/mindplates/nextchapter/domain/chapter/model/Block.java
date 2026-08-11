package com.mindplates.nextchapter.domain.chapter.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 블록 하나. 딜리버리 중립 표현의 최소 단위이고, <b>신호가 붙는 지점</b>이다.
 *
 * @param id {@link BlockIds} 규칙을 따르는 안정 ID. 버전이 올라가도 가능한 한 유지된다
 * @param text 주 텍스트. heading 제목, paragraph 본문, formula 의 LaTeX 소스, narration 대본
 * @param attributes 타입별 구조 (figure 의 {@code spec}, quiz 의 {@code question/choices/answer})
 */
public record Block(String id, BlockType type, String text, Map<String, Object> attributes) {

    /**
     * 출처 근거가 담기는 속성 키.
     *
     * <p>출처를 별도 블록 타입이나 별도 테이블이 아니라 <b>주장을 하는 블록의 속성</b>으로 두는 이유는
     * 검증 패스가 "주장–출처 일치"를 검사하기 때문이다. 둘이 떨어져 있으면 어느 주장에 어느 출처가
     * 붙는지를 다시 추정해야 하고, 그 추정이 틀리면 검증이 통과했다는 사실 자체가 무의미해진다.
     */
    public static final String SOURCES = "sources";

    public Block {
        if (!BlockIds.isValid(id)) {
            throw new IllegalArgumentException("블록 ID 형식이 아닙니다: " + id);
        }
        if (type == null) {
            throw new IllegalArgumentException("블록 타입은 필수입니다.");
        }
        text = Strings.trimToNull(text);
        if (type.isTextRequired() && text == null) {
            throw new IllegalArgumentException(type + " 블록에는 텍스트가 필요합니다. id=" + id);
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        for (String required : type.requiredAttributes()) {
            if (!attributes.containsKey(required) || attributes.get(required) == null) {
                throw new IllegalArgumentException(type + " 블록에 '" + required + "' 속성이 필요합니다. id=" + id);
            }
        }
    }

    public static Block text(String id, BlockType type, String text) {
        return new Block(id, type, text, Map.of());
    }

    public static Block figure(String id, Object spec) {
        return new Block(id, BlockType.FIGURE, null, Map.of("spec", spec));
    }

    public static Block quiz(String id, Object question, Object choices, Object answer) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("question", question);
        attributes.put("choices", choices);
        attributes.put("answer", answer);
        return new Block(id, BlockType.QUIZ, null, attributes);
    }

    /**
     * 내용만 바꾸고 ID 를 유지한다. 수정이 이 메서드를 지나야 하는 이유는 ID 승계가 기본 동작이어야
     * 하기 때문이다 — 새 블록을 만드는 쪽이 기본이 되면 수정마다 신호가 끊긴다.
     */
    public Block withContent(String newText, Map<String, Object> newAttributes) {
        return new Block(id, type, newText, newAttributes);
    }

    /** ID 를 새로 발급받은 사본. 승계에 실패한 블록이 이 경로로 새 ID 를 받는다. */
    public Block withId(String newId) {
        return new Block(newId, type, text, attributes);
    }

    /** 이 블록이 출처를 달고 있는지. 검증 패스가 검사할 대상을 고르는 기준이다. */
    public boolean hasSources() {
        Object sources = attributes.get(SOURCES);
        return sources instanceof java.util.Collection<?> collection && !collection.isEmpty();
    }
}
