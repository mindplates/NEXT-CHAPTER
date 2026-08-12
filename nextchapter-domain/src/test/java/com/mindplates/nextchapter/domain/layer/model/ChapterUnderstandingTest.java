package com.mindplates.nextchapter.domain.layer.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 이해도 판정이 경로 결정과 퀴즈 난이도를 움직인다. 여기서 틀리면 사용자가 <b>엉뚱한 챕터로 보내진다</b> —
 * 에러가 아니라 나쁜 추천으로 나타난다.
 */
@DisplayName("챕터 이해도")
class ChapterUnderstandingTest {

    private static ChapterUnderstanding of(int attempts, int correct, int questions) {
        return new ChapterUnderstanding(100L, attempts, correct, questions, 0, LocalDateTime.now());
    }

    /** 모르는 것과 낮은 것을 합치면 처음 온 사용자를 "막힌 사람"으로 취급해 진입 자체를 막는다. */
    @Test
    @DisplayName("시도가 적으면 UNKNOWN 이다 — 낮은 것과 다르다")
    void tooFewAttemptsIsUnknown() {
        assertThat(ChapterUnderstanding.none(100L).level()).isEqualTo(UnderstandingLevel.UNKNOWN);
        assertThat(of(1, 0, 0).level()).isEqualTo(UnderstandingLevel.UNKNOWN);
    }

    @Test
    @DisplayName("정답률이 낮으면 STRUGGLING 이다")
    void lowRateIsStruggling() {
        assertThat(of(4, 1, 0).level()).isEqualTo(UnderstandingLevel.STRUGGLING);
    }

    @Test
    @DisplayName("정답률이 높으면 CONFIDENT 다")
    void highRateIsConfident() {
        assertThat(of(5, 5, 0).level()).isEqualTo(UnderstandingLevel.CONFIDENT);
        assertThat(of(5, 4, 0).level()).isEqualTo(UnderstandingLevel.CONFIDENT);
    }

    @Test
    @DisplayName("중간은 LEARNING 이다")
    void middleIsLearning() {
        assertThat(of(4, 3, 0).level()).isEqualTo(UnderstandingLevel.LEARNING);
    }

    /** 퀴즈를 풀기 전에 여러 번 물었다면 정답률은 아직 아무것도 말해 주지 않는다. */
    @Test
    @DisplayName("질문이 시도보다 많으면 막힌 것으로 본다")
    void manyQuestionsMeanStruggling() {
        assertThat(of(0, 0, 3).level()).isEqualTo(UnderstandingLevel.STRUGGLING);
        assertThat(of(5, 5, 6).level()).isEqualTo(UnderstandingLevel.STRUGGLING);
    }

    /** 질문 한 건은 막힘의 근거가 되지 못한다 — 한 번 묻고 이해한 경우가 흔하다. */
    @Test
    @DisplayName("질문 한 건은 판정을 바꾸지 않는다")
    void singleQuestionDoesNotFlip() {
        assertThat(of(4, 4, 1).level()).isEqualTo(UnderstandingLevel.CONFIDENT);
    }

    @Test
    @DisplayName("소비 진행과 방문 여부를 구분한다")
    void tracksVisitAndCompletion() {
        ChapterUnderstanding fresh = ChapterUnderstanding.none(100L);
        assertThat(fresh.visited()).isFalse();
        assertThat(fresh.completed()).isFalse();

        ChapterUnderstanding done = new ChapterUnderstanding(100L, 2, 2, 0, 100, LocalDateTime.now());
        assertThat(done.visited()).isTrue();
        assertThat(done.completed()).isTrue();
    }

    @Test
    @DisplayName("진행률은 0~100 으로 접힌다")
    void clampsProgress() {
        assertThat(new ChapterUnderstanding(100L, 0, 0, 0, 150, null).progressPercent())
                .isEqualTo(100);
        assertThat(new ChapterUnderstanding(100L, 0, 0, 0, -10, null).progressPercent())
                .isZero();
    }

    /** 맞힌 수가 시도보다 많으면 집계가 깨진 것이다. 통과시키면 정답률이 1을 넘는다. */
    @Test
    @DisplayName("모순된 집계는 거절한다")
    void rejectsInconsistentCounts() {
        assertThatThrownBy(() -> new ChapterUnderstanding(100L, 1, 2, 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChapterUnderstanding(100L, -1, 0, 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChapterUnderstanding(null, 0, 0, 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("정답률은 시도가 없으면 0 이다")
    void rateWithoutAttempts() {
        assertThat(ChapterUnderstanding.none(100L).correctRate()).isZero();
        assertThat(of(4, 3, 0).correctRate()).isEqualTo(0.75);
    }
}
