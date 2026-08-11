package com.mindplates.nextchapter.domain.generation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 배치 잡이 지켜야 하는 것은 <b>제출 당시의 벤더·모델을 잃지 않는 것</b>이다. 배치는 최대 24시간 살아 있고
 * 그 사이 관리자가 단계 설정을 바꿀 수 있으므로, 조회할 때 현재 설정을 다시 읽으면 다른 벤더에게 다른
 * 벤더의 배치 ID 를 묻게 된다 — 제출한 배치가 조용히 유실된다.
 */
@DisplayName("배치 잡")
class AiBatchJobTest {

    private static AiBatchJob submitted() {
        return AiBatchJob.submitted(5L, GenerationStage.BODY, AiVendor.ANTHROPIC, "claude-opus-5", "msgbatch_abc", 12);
    }

    @Test
    @DisplayName("제출은 SUBMITTED 로 시작하고 벤더·모델을 담는다")
    void startsSubmitted() {
        AiBatchJob job = submitted();

        assertThat(job.status()).isEqualTo(AiBatchStatus.SUBMITTED);
        assertThat(job.status().isTerminal()).isFalse();
        assertThat(job.vendor()).isEqualTo(AiVendor.ANTHROPIC);
        assertThat(job.model()).isEqualTo("claude-opus-5");
        assertThat(job.itemCount()).isEqualTo(12);
    }

    @Test
    @DisplayName("완료 전이는 상태와 완료 시각만 바꾼다")
    void completeKeepsSubmissionFacts() {
        LocalDateTime at = LocalDateTime.of(2026, 8, 12, 10, 0);

        AiBatchJob completed = submitted().completed(at);

        assertThat(completed.status()).isEqualTo(AiBatchStatus.COMPLETED);
        assertThat(completed.completedAt()).isEqualTo(at);
        assertThat(completed.vendor()).isEqualTo(AiVendor.ANTHROPIC);
        assertThat(completed.batchId()).isEqualTo("msgbatch_abc");
    }

    @Test
    @DisplayName("실패는 사유를 남긴다")
    void failureKeepsReason() {
        AiBatchJob failed = submitted().failed("결과 URL 이 없습니다", LocalDateTime.now());

        assertThat(failed.status()).isEqualTo(AiBatchStatus.FAILED);
        assertThat(failed.failureReason()).isEqualTo("결과 URL 이 없습니다");
        assertThat(failed.status().isTerminal()).isTrue();
    }

    /** 같은 배치를 두 번 반영하면 챕터마다 v2 가 쌓인다 — 수정이 아니라 중복이다. */
    @Test
    @DisplayName("이미 끝난 배치는 다시 전이하지 않는다")
    void terminalIsFinal() {
        AiBatchJob completed = submitted().completed(LocalDateTime.now());

        assertThatThrownBy(() -> completed.completed(LocalDateTime.now())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> completed.failed("x", LocalDateTime.now())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("전이는 새 객체를 만든다")
    void transitionIsImmutable() {
        AiBatchJob job = submitted();

        job.completed(LocalDateTime.now());

        assertThat(job.status()).isEqualTo(AiBatchStatus.SUBMITTED);
    }

    @Test
    @DisplayName("배치 ID·모델·뼈대가 없으면 거절한다")
    void requiresIdentity() {
        assertThatThrownBy(() -> AiBatchJob.submitted(null, GenerationStage.BODY, AiVendor.ANTHROPIC, "m", "b", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AiBatchJob.submitted(5L, GenerationStage.BODY, AiVendor.ANTHROPIC, " ", "b", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AiBatchJob.submitted(5L, GenerationStage.BODY, AiVendor.ANTHROPIC, "m", " ", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AiBatchJob.submitted(5L, GenerationStage.BODY, null, "m", "b", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 항목이 0개인 배치를 제출하면 벤더가 거절하고, 잡만 남아 폴러가 영원히 조회한다. */
    @Test
    @DisplayName("항목이 0개면 거절한다")
    void requiresItems() {
        assertThatThrownBy(() -> AiBatchJob.submitted(5L, GenerationStage.BODY, AiVendor.ANTHROPIC, "m", "b", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
