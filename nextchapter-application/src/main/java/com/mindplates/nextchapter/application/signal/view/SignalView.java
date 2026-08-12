package com.mindplates.nextchapter.application.signal.view;

import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.signal.model.Signal;
import com.mindplates.nextchapter.domain.signal.model.SignalType;
import java.time.LocalDateTime;

/**
 * 기록 결과. 페이로드를 되돌려주지 않는다 — 클라이언트가 방금 보낸 값이고, 질문·오류 신고 본문이
 * 응답에 다시 실릴 이유가 없다.
 */
public record SignalView(
        Long id,
        Long chapterId,
        int chapterVersion,
        String blockId,
        DeliveryFormat format,
        SignalType type,
        LocalDateTime occurredAt) {

    public static SignalView from(Signal signal) {
        return new SignalView(
                signal.id(),
                signal.chapterId(),
                signal.chapterVersion(),
                signal.blockId(),
                signal.format(),
                signal.type(),
                signal.occurredAt());
    }
}
