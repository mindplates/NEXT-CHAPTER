package com.mindplates.nextchapter.application.chapter.view;

import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import java.util.List;
import java.util.Map;

/**
 * 사용자에게 내려가는 블록. 도메인 {@link Block} 을 그대로 직렬화하지 않는 <b>유일한 이유는 퀴즈
 * 정답</b>이다.
 *
 * <p>정답이 브라우저에 있으면 채점을 클라이언트가 하게 되고 그 결과는 위조할 수 있다. 오답률은 집단
 * 루프의 핵심 신호이므로, 위조 가능성 자체가 임계치 판정의 근거를 없앤다. 그래서 정답은 내리지 않고
 * 채점은 서버가 한다.
 *
 * <p>운영자 응답({@code ChapterBodyView})은 도메인 블록을 그대로 쓴다 — 검수는 정답을 봐야 성립한다.
 * 그 비대칭이 의도된 것이다.
 */
public record BlockView(String id, BlockType type, String text, Map<String, Object> attributes) {

    public static BlockView forLearner(Block block) {
        return new BlockView(block.id(), block.type(), block.text(), block.learnerAttributes());
    }

    public static List<BlockView> forLearner(List<Block> blocks) {
        return blocks.stream().map(BlockView::forLearner).toList();
    }
}
