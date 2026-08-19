package com.mindplates.nextchapter.domain.signal.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("집단 신호 원장 행")
class CollectiveSignalEventTest {

    private static CollectiveSignalEvent event(String blockId) {
        return new CollectiveSignalEvent(
                1L,
                100L,
                2,
                blockId,
                DeliveryFormat.WEB,
                SignalType.QUESTION,
                null,
                LocalDateTime.of(2026, 8, 12, 9, 0));
    }

    @Test
    @DisplayName("공용 본문 블록이면 만들어진다")
    void createsForBodyBlock() {
        assertThat(event("b6").blockId()).isEqualTo("b6");
    }

    @Test
    @DisplayName("보충 블록은 거절한다 — 여럿이 겹쳐 볼 수 없다")
    void rejectsSupplementBlock() {
        assertThatThrownBy(() -> event("s1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("블록이 없으면 거절한다 — 애초에 블록 단위 집계 대상이 아니다")
    void rejectsMissingBlock() {
        assertThatThrownBy(() -> event(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("신호 ID 가 없으면 거절한다 — 멱등 키가 없어진다")
    void rejectsMissingSignalId() {
        assertThatThrownBy(() -> new CollectiveSignalEvent(
                        null, 100L, 2, "b6", DeliveryFormat.WEB, SignalType.QUESTION, null, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("발생 시각을 주지 않으면 지금으로 채운다")
    void defaultsOccurredAt() {
        CollectiveSignalEvent event =
                new CollectiveSignalEvent(1L, 100L, 2, "b6", DeliveryFormat.WEB, SignalType.QUESTION, null, null);

        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("QUIZ_ANSWER 는 정답 여부를 담을 수 있다")
    void carriesCorrectFlag() {
        CollectiveSignalEvent event = new CollectiveSignalEvent(
                1L, 100L, 2, "b6", DeliveryFormat.WEB, SignalType.QUIZ_ANSWER, false, LocalDateTime.now());

        assertThat(event.correct()).isFalse();
    }
}
