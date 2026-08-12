package com.mindplates.nextchapter.application.layer.port.out;

import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.layer.model.SupplementBlock;
import java.util.Map;

public interface SaveSupplementBlockPort {

    /**
     * 보충 블록을 만든다. <b>블록 ID 는 저장 뒤에 정해진다</b> — 행의 PK 가 곧 ID({@code s{pk}})이므로
     * 호출부가 미리 만들 수 없다.
     *
     * <p>ID 를 PK 에서 얻는 이유는 재사용을 원천에서 막기 위해서다. 순번을 계산해 발급하면 지운 뒤 다시
     * 만들 때 지워진 ID 가 되살아나고, 그러면 두 설명의 신호가 한 블록에 섞인다.
     */
    SupplementBlock create(NewSupplement supplement);

    /**
     * @param chapterVersion 생성 당시 챕터 버전. 앵커가 사라졌는지 판단하는 근거다
     */
    record NewSupplement(
            Long userId,
            Long chapterId,
            int chapterVersion,
            String anchorBlockId,
            BlockType blockType,
            String text,
            Map<String, Object> attributes) {}
}
