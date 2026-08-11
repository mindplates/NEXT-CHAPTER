package com.mindplates.nextchapter.domain.generation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("생성 실패 항목")
class GenerationFailureTest {

    private static GenerationFailure open() {
        return GenerationFailure.open(
                5L,
                101L,
                GenerationStage.BODY,
                "skeleton.body.requested",
                3,
                420L,
                "{\"skeletonId\":5,\"chapterId\":101}",
                "com.example.VendorTimeout",
                "타임아웃",
                3);
    }

    @Nested
    @DisplayName("생성")
    class Creation {

        @Test
        @DisplayName("OPEN 으로 시작하고 메시지 좌표와 페이로드를 담는다")
        void startsOpen() {
            GenerationFailure failure = open();

            assertThat(failure.status()).isEqualTo(GenerationFailureStatus.OPEN);
            assertThat(failure.partition()).isEqualTo(3);
            assertThat(failure.offset()).isEqualTo(420L);
            assertThat(failure.payload()).contains("chapterId");
        }

        /**
         * 뼈대 ID 만 남기면 운영자가 할 수 있는 것은 "처음부터 다시"뿐이고, 그건 이미 성공한 챕터의
         * 생성비를 다시 쓰는 일이다.
         */
        @Test
        @DisplayName("페이로드가 비면 거절한다 — 그것 없이는 재시도가 불가능하다")
        void requiresPayload() {
            assertThatThrownBy(() ->
                            GenerationFailure.open(5L, null, GenerationStage.GRAPH, "t", 0, 0L, "  ", "E", null, 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 읽을 수 없는 메시지가 조용히 사라지는 것이 이 큐를 만든 이유 그 자체다. */
        @Test
        @DisplayName("뼈대를 몰라도 항목을 만들 수 있다 — 페이로드를 읽지 못한 경우다")
        void allowsUnknownSkeleton() {
            GenerationFailure failure = GenerationFailure.open(
                    null, null, GenerationStage.BODY, "t", 0, 0L, "not-json", "JsonParseException", null, 3);

            assertThat(failure.skeletonId()).isNull();
        }

        @Test
        @DisplayName("시도 횟수가 0 이하면 거절한다")
        void requiresAttempts() {
            assertThatThrownBy(() ->
                            GenerationFailure.open(5L, null, GenerationStage.GRAPH, "t", 0, 0L, "{}", "E", null, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 스택 트레이스가 통째로 실려 오면 큐 화면이 읽을 수 없게 된다. */
        @Test
        @DisplayName("긴 오류 메시지는 잘라 담는다")
        void truncatesLongMessage() {
            String longMessage = "x".repeat(5000);

            GenerationFailure failure =
                    GenerationFailure.open(5L, null, GenerationStage.GRAPH, "t", 0, 0L, "{}", "E", longMessage, 1);

            assertThat(failure.errorMessage()).hasSize(2000);
        }
    }

    @Nested
    @DisplayName("단계")
    class Stage {

        /** 실패한 뼈대의 상태는 FAILED 로 덮이므로, 상태만 남기면 재시도할 단계를 알 수 없다. */
        @Test
        @DisplayName("단계마다 되돌릴 뼈대 상태가 정해져 있다")
        void mapsToSkeletonStatus() {
            assertThat(GenerationStage.GRAPH.skeletonStatus()).isEqualTo(SkeletonStatus.GENERATING_GRAPH);
            assertThat(GenerationStage.OUTLINE.skeletonStatus()).isEqualTo(SkeletonStatus.GENERATING_OUTLINES);
            assertThat(GenerationStage.BODY.skeletonStatus()).isEqualTo(SkeletonStatus.GENERATING_BODIES);
            assertThat(GenerationStage.ASSET.skeletonStatus()).isEqualTo(SkeletonStatus.GENERATING_ASSETS);
        }

        @Test
        @DisplayName("본문·자산만 챕터 단위 실패다")
        void perChapterStages() {
            assertThat(GenerationStage.BODY.isPerChapter()).isTrue();
            assertThat(GenerationStage.ASSET.isPerChapter()).isTrue();
            assertThat(GenerationStage.GRAPH.isPerChapter()).isFalse();
            assertThat(GenerationStage.OUTLINE.isPerChapter()).isFalse();
        }

        /** FAILED 에서 그 단계로 되돌아가는 것이 운영자 재시도 경로다. */
        @Test
        @DisplayName("실패한 뼈대는 어느 생성 단계로도 되돌아갈 수 있다")
        void failedCanReturnToAnyStage() {
            for (GenerationStage stage : GenerationStage.values()) {
                assertThat(SkeletonStatus.FAILED.canTransitionTo(stage.skeletonStatus()))
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class Transitions {

        /** 재시도는 성공을 뜻하지 않는다. 닫아 버리면 같은 원인이 몇 번 반복됐는지 알 수 없다. */
        @Test
        @DisplayName("재시도는 RETRIED 로 두고 닫지 않는다")
        void retryDoesNotClose() {
            GenerationFailure retried = open().retried("admin");

            assertThat(retried.status()).isEqualTo(GenerationFailureStatus.RETRIED);
            assertThat(retried.status().isClosed()).isFalse();
            assertThat(retried.resolvedBy()).isEqualTo("admin");
            assertThat(retried.resolvedAt()).isNull();
        }

        @Test
        @DisplayName("재시도한 항목도 나중에 닫을 수 있다")
        void retriedCanBeResolved() {
            LocalDateTime at = LocalDateTime.of(2026, 8, 12, 10, 0);

            GenerationFailure resolved = open().retried("admin").resolved("admin", at);

            assertThat(resolved.status()).isEqualTo(GenerationFailureStatus.RESOLVED);
            assertThat(resolved.resolvedAt()).isEqualTo(at);
        }

        @Test
        @DisplayName("닫힌 항목은 다시 건드릴 수 없다")
        void closedIsFinal() {
            GenerationFailure resolved = open().resolved("admin", LocalDateTime.now());

            assertThatThrownBy(() -> resolved.retried("admin")).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("전이는 새 객체를 만든다")
        void transitionIsImmutable() {
            GenerationFailure original = open();

            original.retried("admin");

            assertThat(original.status()).isEqualTo(GenerationFailureStatus.OPEN);
        }
    }
}
