package com.mindplates.nextchapter.application.layer.view;

import com.mindplates.nextchapter.application.chapter.view.BlockView;
import com.mindplates.nextchapter.domain.layer.model.SupplementBlock;

/**
 * 보충 블록 응답.
 *
 * @param reused 새로 만들지 않고 이미 있던 것을 돌려줬는지. 화면이 "방금 만들었습니다"와 "전에 만든
 *     설명입니다"를 구분할 수 있어야 하고, 개발 중에는 이 값이 곧 비용 방어선이 동작하는지의 확인이다
 */
public record SupplementView(String anchorBlockId, BlockView block, boolean reused) {

    public static SupplementView of(SupplementBlock supplement, boolean reused) {
        return new SupplementView(supplement.anchorBlockId(), BlockView.forLearner(supplement.block()), reused);
    }
}
