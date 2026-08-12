package com.mindplates.nextchapter.application.signal.view;

import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.signal.model.Signal;
import com.mindplates.nextchapter.domain.signal.model.SignalType;
import java.time.LocalDateTime;

/**
 * 기록 결과.
 *
 * <p>페이로드를 통째로 되돌려주지 않는다 — 클라이언트가 방금 보낸 값이고, 질문·오류 신고 본문이 응답에
 * 다시 실릴 이유가 없다.
 *
 * @param correct <b>채점 결과.</b> 이것만은 되돌려준다 — 서버가 채점하므로 클라이언트가 알 방법이 없다.
 *     정답을 내려 주지 않기로 한 결정의 짝이다: 정답을 숨기면 결과를 알려 줘야 화면이 성립한다. 퀴즈 응답이
 *     아니면 null 이다
 */
public record SignalView(
        Long id,
        Long chapterId,
        int chapterVersion,
        String blockId,
        DeliveryFormat format,
        SignalType type,
        Boolean correct,
        LocalDateTime occurredAt) {

    private static final String CORRECT_KEY = "correct";

    public static SignalView from(Signal signal) {
        return new SignalView(
                signal.id(),
                signal.chapterId(),
                signal.chapterVersion(),
                signal.blockId(),
                signal.format(),
                signal.type(),
                gradeOf(signal),
                signal.occurredAt());
    }

    private static Boolean gradeOf(Signal signal) {
        if (signal.type() != SignalType.QUIZ_ANSWER) {
            return null;
        }
        Object correct = signal.payload().get(CORRECT_KEY);
        return correct instanceof Boolean value ? value : null;
    }
}
