package com.mindplates.nextchapter.common.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("문자열 유틸")
class StringsTest {

    @Test
    @DisplayName("공백만 있는 값은 필수 검증에서 걸린다")
    void requireTextRejectsBlank() {
        assertThatThrownBy(() -> Strings.requireText("   ", "이름"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이름");
    }

    @Test
    @DisplayName("표기가 다른 같은 표현은 같은 정규화 값이 된다")
    void normalizeCollapsesSpacingAndPunctuation() {
        assertThat(Strings.normalizeForMatch("기계 학습")).isEqualTo(Strings.normalizeForMatch("기계학습"));
        assertThat(Strings.normalizeForMatch("ML 입문!")).isEqualTo(Strings.normalizeForMatch("ml입문"));
        assertThat(Strings.normalizeForMatch("Machine-Learning"))
                .isEqualTo(Strings.normalizeForMatch("machine learning"));
    }

    @Test
    @DisplayName("전각 영문과 반각 영문은 같은 값으로 접힌다")
    void normalizeFoldsFullWidth() {
        assertThat(Strings.normalizeForMatch("ＭＬ")).isEqualTo(Strings.normalizeForMatch("ml"));
    }

    @Test
    @DisplayName("비교할 문자가 없으면 null 이다")
    void normalizeReturnsNullWhenNothingRemains() {
        assertThat(Strings.normalizeForMatch("!!!")).isNull();
        assertThat(Strings.normalizeForMatch(null)).isNull();
    }

    @Test
    @DisplayName("빈 문자열은 null 로 정리된다")
    void trimToNull() {
        assertThat(Strings.trimToNull("  ")).isNull();
        assertThat(Strings.trimToNull("  값  ")).isEqualTo("값");
    }
}
