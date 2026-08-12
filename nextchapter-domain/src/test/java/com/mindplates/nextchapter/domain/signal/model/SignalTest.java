package com.mindplates.nextchapter.domain.signal.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 신호가 지켜야 하는 것은 <b>집계에 기여할 수 있는 형태</b>다. 블록 없는 질문은 어느 지점을 고칠지 알려
 * 주지 못하고, 정답 여부 없는 퀴즈 응답은 오답률을 만들지 못한다 — 둘 다 저장은 되면서 건수만 늘린다.
 */
@DisplayName("신호")
class SignalTest {

    private static Signal quizAnswer(String blockId) {
        return Signal.of(42L, 100L, 2, blockId, DeliveryFormat.WEB, SignalType.QUIZ_ANSWER, Map.of("correct", false));
    }

    @Test
    @DisplayName("퀴즈 응답은 블록과 정답 여부를 든다")
    void quizAnswerCarriesBlockAndCorrectness() {
        Signal signal = quizAnswer("b6");

        assertThat(signal.blockId()).isEqualTo("b6");
        assertThat(signal.payload()).containsEntry("correct", false);
        assertThat(signal.occurredAt()).isNotNull();
        assertThat(signal.isPublished()).isFalse();
    }

    /** 챕터 전체에 붙으면 "이 챕터가 어렵다"까지만 알 수 있다 — 어느 문단인지가 나와야 수정안이 나온다. */
    @Test
    @DisplayName("질문·퀴즈·오류 신고는 블록이 없으면 거절한다")
    void blockIsRequiredExceptProgress() {
        assertThatThrownBy(() -> quizAnswer(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        Signal.of(42L, 100L, 2, null, DeliveryFormat.WEB, SignalType.QUESTION, Map.of("text", "왜?")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Signal.of(
                        42L, 100L, 2, null, DeliveryFormat.WEB, SignalType.ERROR_REPORT, Map.of("text", "틀렸다")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 소비 진행은 본래 챕터 단위 사실이다. 블록을 강제하면 그 신호를 만들 수 없다. */
    @Test
    @DisplayName("소비 진행만 블록 없이 기록된다")
    void progressNeedsNoBlock() {
        Signal signal = Signal.of(42L, 100L, 2, null, DeliveryFormat.WEB, SignalType.PROGRESS, Map.of("percent", 60));

        assertThat(signal.blockId()).isNull();
        assertThat(signal.type().isBlockRequired()).isFalse();
    }

    @Test
    @DisplayName("필수 페이로드가 없으면 거절한다")
    void requiresPayloadKeys() {
        assertThatThrownBy(() -> Signal.of(42L, 100L, 2, "b6", DeliveryFormat.WEB, SignalType.QUIZ_ANSWER, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correct");
        assertThatThrownBy(() -> Signal.of(42L, 100L, 2, "b6", DeliveryFormat.WEB, SignalType.QUESTION, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
        assertThatThrownBy(() -> Signal.of(42L, 100L, 2, null, DeliveryFormat.WEB, SignalType.PROGRESS, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("percent");
    }

    /** 0 은 "본문 없음"이다. 그 신호는 어느 버전과도 비교할 수 없어 수정 전후 비교의 기준선이 되지 못한다. */
    @Test
    @DisplayName("소비한 버전이 없으면 거절한다")
    void requiresConsumedVersion() {
        assertThatThrownBy(() -> Signal.of(
                        42L, 100L, 0, "b6", DeliveryFormat.WEB, SignalType.QUIZ_ANSWER, Map.of("correct", true)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("사용자·챕터·형태·종류는 필수다")
    void requiresIdentity() {
        assertThatThrownBy(() -> Signal.of(
                        null, 100L, 2, "b6", DeliveryFormat.WEB, SignalType.QUIZ_ANSWER, Map.of("correct", true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Signal.of(
                        42L, null, 2, "b6", DeliveryFormat.WEB, SignalType.QUIZ_ANSWER, Map.of("correct", true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Signal.of(42L, 100L, 2, "b6", null, SignalType.QUIZ_ANSWER, Map.of("correct", true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Signal.of(42L, 100L, 2, "b6", DeliveryFormat.WEB, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("블록 ID 형식이 틀리면 거절한다")
    void rejectsMalformedBlockId() {
        assertThatThrownBy(() -> quizAnswer("block-6")).isInstanceOf(IllegalArgumentException.class);
    }

    /** 개인화 블록에 붙은 신호를 집단 집계에서 구분해야 한다 — 공용 본문에는 없는 블록이다. */
    @Test
    @DisplayName("보충 블록에 붙은 신호를 구분할 수 있다")
    void marksSupplementBlockSignals() {
        assertThat(quizAnswer("s3").isOnSupplementBlock()).isTrue();
        assertThat(quizAnswer("b3").isOnSupplementBlock()).isFalse();
    }

    @Test
    @DisplayName("발행 표시는 새 객체를 만든다")
    void publishIsImmutable() {
        Signal signal = quizAnswer("b6");

        Signal published = signal.published(LocalDateTime.of(2026, 8, 12, 10, 0));

        assertThat(published.isPublished()).isTrue();
        assertThat(published.publishedAt()).isEqualTo(LocalDateTime.of(2026, 8, 12, 10, 0));
        assertThat(signal.isPublished()).isFalse();
    }

    /** 페이로드를 밖에서 고칠 수 있으면 저장된 신호와 집계 대상이 달라질 수 있다. */
    @Test
    @DisplayName("페이로드는 복사된다")
    void payloadIsCopied() {
        Map<String, Object> mutable = new java.util.HashMap<>();
        mutable.put("correct", true);
        Signal signal = Signal.of(42L, 100L, 2, "b6", DeliveryFormat.WEB, SignalType.QUIZ_ANSWER, mutable);

        mutable.put("correct", false);

        assertThat(signal.payload()).containsEntry("correct", true);
    }
}
