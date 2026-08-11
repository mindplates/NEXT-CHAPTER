package com.mindplates.nextchapter.domain.skeleton.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("뼈대")
class SkeletonTest {

    @Test
    @DisplayName("생성 시작 상태는 그래프 생성이다")
    void startsAtGraphGeneration() {
        Skeleton skeleton = Skeleton.start(42L);

        assertThat(skeleton.status()).isEqualTo(SkeletonStatus.GENERATING_GRAPH);
        assertThat(skeleton.isPublished()).isFalse();
    }

    @Test
    @DisplayName("주제 없이 만들 수 없다 — 뼈대는 주제에 1:1 로 붙는다")
    void requiresTopic() {
        assertThatThrownBy(() -> Skeleton.start(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("주제");
    }

    @Test
    @DisplayName("published 와 failed 만 종료 상태다")
    void terminalStates() {
        assertThat(SkeletonStatus.PUBLISHED.isTerminal()).isTrue();
        assertThat(SkeletonStatus.FAILED.isTerminal()).isTrue();
        assertThat(SkeletonStatus.GENERATING_ASSETS.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("상태 전이는 새 인스턴스를 만든다")
    void withStatusIsImmutable() {
        Skeleton skeleton = new Skeleton(1L, 42L, SkeletonStatus.GENERATING_BODIES, null, null);

        Skeleton advanced = skeleton.withStatus(SkeletonStatus.PUBLISHED);

        assertThat(skeleton.status()).isEqualTo(SkeletonStatus.GENERATING_BODIES);
        assertThat(advanced.status()).isEqualTo(SkeletonStatus.PUBLISHED);
        assertThat(advanced.isPublished()).isTrue();
    }
}
