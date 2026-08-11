package com.mindplates.nextchapter.domain.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("주제 임베딩")
class TopicEmbeddingTest {

    @Test
    @DisplayName("차원은 벡터 길이에서 나온다")
    void dimensionFollowsVector() {
        assertThat(TopicEmbedding.of(1L, "voyage-3-large", "머신러닝", new float[] {0.1f, 0.2f, 0.3f})
                        .dimension())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("차원과 벡터 길이가 다르면 거절한다 — 저장 후에는 조용히 틀린다")
    void rejectsDimensionMismatch() {
        assertThatThrownBy(() -> new TopicEmbedding(1L, "m", 5, "텍스트", new float[] {0.1f}, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("차원");
    }

    @Test
    @DisplayName("빈 벡터는 거절한다")
    void rejectsEmptyVector() {
        assertThatThrownBy(() -> TopicEmbedding.of(1L, "m", "텍스트", new float[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("모델은 필수다 — 어느 모델로 만든 벡터인지 모르면 비교할 수 없다")
    void modelIsRequired() {
        assertThatThrownBy(() -> TopicEmbedding.of(1L, "  ", "텍스트", new float[] {0.1f}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("배열을 복제해 들고 나간다 — 호출자가 고쳐도 저장된 값이 바뀌지 않는다")
    void copiesVector() {
        float[] source = {0.1f, 0.2f};
        TopicEmbedding embedding = TopicEmbedding.of(1L, "m", "텍스트", source);

        source[0] = 9.9f;
        float[] read = embedding.vector();
        read[1] = 8.8f;

        assertThat(embedding.vector()).containsExactly(0.1f, 0.2f);
    }

    @Test
    @DisplayName("원천 텍스트에 이름 · 별칭 · 설명이 모두 들어간다")
    void sourceTextCombinesNameAliasesDescription() {
        CatalogTopic topic = new CatalogTopic(1L, 2L, "machine-learning", "머신러닝", "경사하강법", 0, null, null);

        String text =
                TopicEmbedding.sourceTextOf(topic, List.of(TopicAlias.create(1L, "기계학습"), TopicAlias.create(1L, "ML")));

        assertThat(text).contains("머신러닝").contains("기계학습").contains("ML").contains("경사하강법");
    }
}
