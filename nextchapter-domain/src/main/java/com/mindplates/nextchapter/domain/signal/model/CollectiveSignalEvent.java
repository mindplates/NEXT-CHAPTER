package com.mindplates.nextchapter.domain.signal.model;

import com.mindplates.nextchapter.domain.chapter.model.BlockIds;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import java.time.LocalDateTime;

/**
 * 집단 루프 집계 원장의 행 하나 — {@link Signal} 이 Kafka 를 거쳐 컨슈머에 도착한 사본이다.
 *
 * <p>{@code signalId} 를 그대로 멱등 키로 쓴다. Kafka 는 at-least-once 라 같은 신호가 두 번 올 수 있고,
 * 카운터를 직접 올리면 그 중복이 집계를 두 번 센다 — 저장을 {@code signalId} 로 멱등하게 만들면 재수신은
 * 두 번째 저장이 아무 것도 하지 않는 것으로 끝난다.
 *
 * <p>공용 본문 블록({@code b*})만 담는다. 보충 블록({@code s*})은 그 사용자에게만 있어 여럿의 반응을
 * 겹쳐 볼 수 없고, {@code PROGRESS} 처럼 블록이 없는 신호는 애초에 블록 단위 집계 대상이 아니다. 그
 * 필터링은 컨슈머(어댑터)가 먼저 하고, 여기서는 통과한 값이 실제로 유효한 본문 블록인지 다시 확인한다.
 */
public record CollectiveSignalEvent(
        Long signalId,
        Long chapterId,
        int chapterVersion,
        String blockId,
        DeliveryFormat format,
        SignalType type,
        Boolean correct,
        LocalDateTime occurredAt) {

    public CollectiveSignalEvent {
        if (signalId == null) {
            throw new IllegalArgumentException("집단 신호에는 원본 신호 ID 가 필요합니다.");
        }
        if (chapterId == null) {
            throw new IllegalArgumentException("집단 신호에는 챕터가 필요합니다.");
        }
        if (chapterVersion < 1) {
            throw new IllegalArgumentException("소비한 챕터 버전이 필요합니다: " + chapterVersion);
        }
        if (!BlockIds.isBody(blockId)) {
            throw new IllegalArgumentException("집단 집계는 공용 본문 블록만 대상입니다: " + blockId);
        }
        if (format == null) {
            throw new IllegalArgumentException("딜리버리 형태는 필수입니다.");
        }
        if (type == null) {
            throw new IllegalArgumentException("신호 종류는 필수입니다.");
        }
        occurredAt = occurredAt == null ? LocalDateTime.now() : occurredAt;
    }
}
