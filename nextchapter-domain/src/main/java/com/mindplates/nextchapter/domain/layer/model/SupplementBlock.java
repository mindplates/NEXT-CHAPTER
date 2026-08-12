package com.mindplates.nextchapter.domain.layer.model;

import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockIds;

/**
 * 막힌 지점에 끼워 넣는 개인화 블록.
 *
 * <p><b>본문을 고치지 않고 그 뒤에 끼운다.</b> 본문을 사용자마다 다시 쓰면 여러 사람이 각자 다른 텍스트를
 * 본 것이 되어 "같은 지점에서 틀렸다"를 비교할 수 없고, 그 비교가 이 제품의 핵심 메커니즘이다. 그래서
 * 타입 자체가 <b>삽입만</b> 표현한다 — 앵커와 새 블록만 들고, 기존 블록을 가리켜 바꾸는 수단이 없다.
 *
 * @param anchorBlockId 이 블록 <b>뒤에</b> 삽입된다. 공용 본문 블록 ID 다
 * @param block 삽입할 블록. ID 는 보충 공간({@code s{n}})이어야 한다 — 공용 공간을 쓰면 개인화 블록에
 *     붙은 신호가 공용 블록 신호와 섞인다
 */
public record SupplementBlock(String anchorBlockId, Block block) {

    public SupplementBlock {
        if (!BlockIds.isBody(anchorBlockId)) {
            throw new IllegalArgumentException("앵커는 공용 본문 블록 ID 여야 합니다: " + anchorBlockId);
        }
        if (block == null) {
            throw new IllegalArgumentException("보충 블록은 필수입니다.");
        }
        if (!BlockIds.isSupplement(block.id())) {
            throw new IllegalArgumentException("보충 블록 ID 는 보충 공간이어야 합니다: " + block.id());
        }
    }
}
