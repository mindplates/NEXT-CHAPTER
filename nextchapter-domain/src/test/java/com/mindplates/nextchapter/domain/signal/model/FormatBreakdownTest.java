package com.mindplates.nextchapter.domain.signal.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("형태별 분해")
class FormatBreakdownTest {

    @Test
    @DisplayName("오답률을 계산한다")
    void computesWrongRate() {
        FormatBreakdown breakdown = new FormatBreakdown(DeliveryFormat.WEB, 20, 9);

        assertThat(breakdown.wrongRate()).isCloseTo(0.45, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("시도가 없으면 오답률은 0이다")
    void zeroRateWhenNoAttempts() {
        assertThat(new FormatBreakdown(DeliveryFormat.VIDEO, 0, 0).wrongRate()).isZero();
    }

    @Test
    @DisplayName("형태가 없으면 거절한다")
    void rejectsMissingFormat() {
        assertThatThrownBy(() -> new FormatBreakdown(null, 1, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
