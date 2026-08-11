package com.mindplates.nextchapter.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("앱 기준 시간대")
class AppTimeTest {

    @Test
    @DisplayName("기준 시간대는 KST 다")
    void kst() {
        assertThat(AppTime.KST).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    /** 발송·표시 흐름이 시간대 문자열 하나 때문에 실패하면 안 된다. 폴백이 있는 것이 요점이다. */
    @Test
    @DisplayName("무효한 시간대 문자열은 KST 로 폴백한다")
    void fallsBackToKst() {
        assertThat(AppTime.zoneOrDefault(null)).isEqualTo(AppTime.KST);
        assertThat(AppTime.zoneOrDefault("   ")).isEqualTo(AppTime.KST);
        assertThat(AppTime.zoneOrDefault("Not/AZone")).isEqualTo(AppTime.KST);
    }

    @Test
    @DisplayName("유효한 시간대는 그대로 해석한다")
    void parsesValidZone() {
        assertThat(AppTime.zoneOrDefault(" America/New_York ")).isEqualTo(ZoneId.of("America/New_York"));
    }
}
