package com.mindplates.nextchapter.domain.chapter.model;

import java.util.Map;

/**
 * 아직 ID 가 확정되지 않은 블록 — 모델이 돌려준 그대로의 제안.
 *
 * <p>{@link Block} 과 나누는 이유는 ID 검증 시점이다. {@code Block} 은 유효한 ID 를 요구하는데, 모델은
 * ID 를 빠뜨리거나 형식을 틀리거나 "new" 같은 값을 넣는다. 제안을 그대로 {@code Block} 으로 만들려
 * 하면 그 순간 예외가 나고, 승계 규칙이 개입할 자리가 없어진다.
 *
 * @param rawId 모델이 제시한 ID. null 이거나 형식이 틀릴 수 있다 — 그 판정은 승계 규칙이 한다
 */
public record ProposedBlock(String rawId, BlockType type, String text, Map<String, Object> attributes) {

    public ProposedBlock {
        if (type == null) {
            throw new IllegalArgumentException("블록 타입은 필수입니다.");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static ProposedBlock text(BlockType type, String text) {
        return new ProposedBlock(null, type, text, Map.of());
    }

    public static ProposedBlock inheriting(String rawId, BlockType type, String text, Map<String, Object> attributes) {
        return new ProposedBlock(rawId, type, text, attributes);
    }

    /** 확정된 ID 를 붙여 블록으로 만든다. 타입별 필수 항목 검증은 {@link Block} 이 한다. */
    public Block toBlock(String blockId) {
        return new Block(blockId, type, text, attributes);
    }
}
