package com.mindplates.nextchapter.domain.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("주제 별칭")
class TopicAliasTest {

    @Test
    @DisplayName("표기가 다른 같은 표현은 같은 정규화 값을 갖는다")
    void normalizedIsStableAcrossSpacing() {
        TopicAlias spaced = TopicAlias.create(1L, "기계 학습");
        TopicAlias tight = TopicAlias.create(1L, "기계학습");

        assertThat(spaced.normalized()).isEqualTo(tight.normalized());
    }

    @Test
    @DisplayName("원문은 입력한 그대로 남는다 — 정규화는 대조용이다")
    void keepsOriginalText() {
        assertThat(TopicAlias.create(1L, "ML 입문").alias()).isEqualTo("ML 입문");
    }

    @Test
    @DisplayName("비교할 문자가 없는 별칭은 거절한다")
    void rejectsPunctuationOnly() {
        assertThatThrownBy(() -> TopicAlias.create(1L, "!!!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비교할 수 있는 문자");
    }

    @Test
    @DisplayName("주제 없이 만들 수 없다")
    void requiresTopic() {
        assertThatThrownBy(() -> TopicAlias.create(null, "머신러닝")).isInstanceOf(IllegalArgumentException.class);
    }
}
