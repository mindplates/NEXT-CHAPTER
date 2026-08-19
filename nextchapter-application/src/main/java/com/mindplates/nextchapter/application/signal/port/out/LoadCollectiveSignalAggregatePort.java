package com.mindplates.nextchapter.application.signal.port.out;

import com.mindplates.nextchapter.domain.signal.model.BlockSignalAggregate;
import com.mindplates.nextchapter.domain.signal.model.FormatBreakdown;
import java.util.List;

public interface LoadCollectiveSignalAggregatePort {

    /** 블록 단위 집계 — 질문 수, 시도 수, 오답 수. 딜리버리 형태는 나누지 않는다. */
    BlockSignalAggregate aggregate(Long chapterId, int chapterVersion, String blockId);

    /** 같은 집계를 딜리버리 형태별로 나눈다. 임계치 판정에는 쓰지 않고 수정안의 근거로만 쓴다. */
    List<FormatBreakdown> formatBreakdown(Long chapterId, int chapterVersion, String blockId);
}
