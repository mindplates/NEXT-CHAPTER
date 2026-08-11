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
    void transitionIsImmutable() {
        Skeleton skeleton = new Skeleton(1L, 42L, SkeletonStatus.GENERATING_ASSETS, null, null);

        Skeleton advanced = skeleton.transitionTo(SkeletonStatus.PUBLISHED);

        assertThat(skeleton.status()).isEqualTo(SkeletonStatus.GENERATING_ASSETS);
        assertThat(advanced.status()).isEqualTo(SkeletonStatus.PUBLISHED);
        assertThat(advanced.isPublished()).isTrue();
    }

    @Test
    @DisplayName("생성 단계는 한 칸씩만 앞으로 간다 — 건너뛴 실패는 생성이 끝난 뒤에야 드러난다")
    void advancesOneStageAtATime() {
        Skeleton skeleton = Skeleton.start(42L);

        assertThat(skeleton.advance().status()).isEqualTo(SkeletonStatus.GENERATING_OUTLINES);
        assertThatThrownBy(() -> skeleton.transitionTo(SkeletonStatus.GENERATING_BODIES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용되지 않은");
    }

    @Test
    @DisplayName("어느 생성 단계에서든 실패할 수 있고, 실패에서 그 단계로 되돌아갈 수 있다")
    void failureAndRetry() {
        Skeleton failed = Skeleton.start(42L).advance().fail();

        assertThat(failed.status()).isEqualTo(SkeletonStatus.FAILED);
        assertThat(failed.transitionTo(SkeletonStatus.GENERATING_OUTLINES).status())
                .isEqualTo(SkeletonStatus.GENERATING_OUTLINES);
    }

    /** 되돌리면 "재생성 중에는 형태를 잠근다"가 되어, 개선이 잦을수록 서비스가 나빠지는 역전이 생긴다. */
    @Test
    @DisplayName("published 는 되돌아가지 않는다")
    void publishedIsFinal() {
        Skeleton published = new Skeleton(1L, 42L, SkeletonStatus.PUBLISHED, null, null);

        assertThatThrownBy(() -> published.transitionTo(SkeletonStatus.GENERATING_ASSETS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> published.fail()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("published 와 failed 에는 다음 단계가 없다")
    void terminalHasNoNext() {
        assertThatThrownBy(SkeletonStatus.PUBLISHED::next).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(SkeletonStatus.FAILED::next).isInstanceOf(IllegalStateException.class);
    }
}
