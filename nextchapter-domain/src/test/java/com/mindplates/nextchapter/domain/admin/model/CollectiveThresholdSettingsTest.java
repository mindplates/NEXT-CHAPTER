package com.mindplates.nextchapter.domain.admin.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.domain.signal.model.BlockSignalAggregate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CLAUDE.md 예시 그대로 — 같은 지점 질문 5건, 또는 시도 20회 이상에서 오답률 40% 초과. */
@DisplayName("집단 루프 임계치")
class CollectiveThresholdSettingsTest {

    private static final CollectiveThresholdSettings DEFAULTS =
            new CollectiveThresholdSettings(5, 20, 40, null, null, null);

    private static BlockSignalAggregate aggregate(long questionCount, long attemptCount, long wrongCount) {
        return new BlockSignalAggregate(100L, 2, "b6", questionCount, attemptCount, wrongCount);
    }

    @Test
    @DisplayName("질문이 임계치에 도달하면 트리거된다")
    void triggersOnQuestionCount() {
        assertThat(DEFAULTS.isTriggered(aggregate(5, 0, 0))).isTrue();
    }

    @Test
    @DisplayName("질문이 임계치 미만이면 트리거되지 않는다")
    void doesNotTriggerBelowQuestionThreshold() {
        assertThat(DEFAULTS.isTriggered(aggregate(4, 0, 0))).isFalse();
    }

    @Test
    @DisplayName("시도가 최소치를 넘고 오답률이 임계치를 초과하면 트리거된다")
    void triggersOnWrongRate() {
        // 20회 시도에 9회 오답 = 45% > 40%
        assertThat(DEFAULTS.isTriggered(aggregate(0, 20, 9))).isTrue();
    }

    /** 소비자 3명 중 1명만 틀려도 33% 다 — 표본이 작을 때 오답률만으로 판정하면 오작동한다. */
    @Test
    @DisplayName("시도가 최소치 미만이면 오답률이 높아도 트리거되지 않는다")
    void doesNotTriggerBelowMinAttempts() {
        assertThat(DEFAULTS.isTriggered(aggregate(0, 3, 1))).isFalse();
    }

    @Test
    @DisplayName("오답률이 임계치 이하면 트리거되지 않는다")
    void doesNotTriggerAtOrBelowWrongRateThreshold() {
        // 20회 시도에 8회 오답 = 정확히 40%. "초과"만 트리거한다.
        assertThat(DEFAULTS.isTriggered(aggregate(0, 20, 8))).isFalse();
    }

    @Test
    @DisplayName("질문 임계치가 1 미만이면 거절한다")
    void rejectsNonPositiveQuestionThreshold() {
        assertThatThrownBy(() -> new CollectiveThresholdSettings(0, 20, 40, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("오답률 임계치가 범위를 벗어나면 거절한다")
    void rejectsOutOfRangeWrongRate() {
        assertThatThrownBy(() -> new CollectiveThresholdSettings(5, 20, 101, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
