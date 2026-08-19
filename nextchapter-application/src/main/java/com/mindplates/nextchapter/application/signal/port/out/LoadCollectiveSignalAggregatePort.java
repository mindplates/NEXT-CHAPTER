package com.mindplates.nextchapter.application.signal.port.out;

import com.mindplates.nextchapter.domain.signal.model.BlockSignalAggregate;

public interface LoadCollectiveSignalAggregatePort {

    /** 블록 단위 집계 — 질문 수, 시도 수, 오답 수. 딜리버리 형태는 나누지 않는다. */
    BlockSignalAggregate aggregate(Long chapterId, int chapterVersion, String blockId);
}
