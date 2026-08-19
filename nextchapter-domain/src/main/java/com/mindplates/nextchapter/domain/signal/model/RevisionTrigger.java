package com.mindplates.nextchapter.domain.signal.model;

import com.mindplates.nextchapter.domain.chapter.model.BlockIds;
import java.time.LocalDateTime;

/**
 * 블록 하나가 임계치를 넘어 뼈대 수정이 트리거된 사례. 이 레코드가 존재한다는 사실 자체가 3개월
 * 성공 기준의 필수 항목이다 — "사용자 신호가 집계되어 뼈대 수정을 트리거한 사례가 존재한다."
 *
 * <p>{@code (chapterId, chapterVersion, blockId)} 로 한 번만 남는다. 같은 버전의 같은 블록에 신호가
 * 계속 쌓여도 트리거는 한 번이면 충분하다 — P5.3 이 근거를 생성하고 P5.4 승인 대기열에 오를 때까지
 * 그 한 건이 상태를 갖는다. 반복 트리거는 같은 항목을 다시 큐에 올리는 것에 불과하다.
 */
public record RevisionTrigger(
        Long id,
        Long chapterId,
        int chapterVersion,
        String blockId,
        long questionCount,
        long attemptCount,
        long wrongCount,
        LocalDateTime triggeredAt) {

    public RevisionTrigger {
        if (chapterId == null) {
            throw new IllegalArgumentException("트리거에는 챕터가 필요합니다.");
        }
        if (chapterVersion < 1) {
            throw new IllegalArgumentException("트리거에는 소비 버전이 필요합니다: " + chapterVersion);
        }
        if (!BlockIds.isBody(blockId)) {
            throw new IllegalArgumentException("트리거는 공용 본문 블록에만 붙습니다: " + blockId);
        }
        triggeredAt = triggeredAt == null ? LocalDateTime.now() : triggeredAt;
    }

    public static RevisionTrigger of(BlockSignalAggregate aggregate) {
        return new RevisionTrigger(
                null,
                aggregate.chapterId(),
                aggregate.chapterVersion(),
                aggregate.blockId(),
                aggregate.questionCount(),
                aggregate.attemptCount(),
                aggregate.wrongCount(),
                null);
    }
}
