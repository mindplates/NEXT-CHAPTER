package com.mindplates.nextchapter.adapter.out.persistence.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindplates.nextchapter.adapter.out.persistence.AbstractPostgresIT;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import com.mindplates.nextchapter.domain.generation.model.AiBatchJob;
import com.mindplates.nextchapter.domain.generation.model.AiBatchStatus;
import com.mindplates.nextchapter.domain.generation.model.GenerationStage;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 배치 잡의 위험은 <b>중복 반영</b>과 <b>제출 사실의 소실</b>이다. 같은 배치를 두 줄로 두면 폴러가 두 번
 * 반영해 챕터마다 v2 가 쌓이고, 제출 당시의 벤더·모델이 사라지면 조회가 다른 벤더에게 간다. 둘 다 목으로는
 * 드러나지 않는다.
 */
@Import(AiBatchJobPersistenceAdapter.class)
@DisplayName("배치 잡 어댑터 (실제 PostgreSQL)")
class AiBatchJobPersistenceAdapterIT extends AbstractPostgresIT {

    @Autowired
    AiBatchJobPersistenceAdapter batchJobs;

    @Autowired
    EntityManager entityManager;

    private static AiBatchJob submitted(Long skeletonId, String batchId) {
        return AiBatchJob.submitted(skeletonId, GenerationStage.BODY, AiVendor.ANTHROPIC, "claude-opus-5", batchId, 12);
    }

    @Test
    @DisplayName("제출 당시의 벤더·모델·항목 수가 그대로 저장된다")
    void savesSubmissionFacts() {
        AiBatchJob saved = batchJobs.save(submitted(5L, "msgbatch_a"));
        entityManager.flush();

        assertThat(saved.id()).isNotNull();
        assertThat(saved.status()).isEqualTo(AiBatchStatus.SUBMITTED);
        assertThat(saved.createdAt()).isNotNull();
        assertThat(batchJobs.findBySkeletonId(5L)).singleElement().satisfies(job -> {
            assertThat(job.vendor()).isEqualTo(AiVendor.ANTHROPIC);
            assertThat(job.model()).isEqualTo("claude-opus-5");
            assertThat(job.itemCount()).isEqualTo(12);
        });
    }

    /** 같은 배치를 두 줄로 두면 폴러가 두 번 반영하고, 그러면 챕터마다 v2 가 쌓인다. */
    @Test
    @DisplayName("같은 배치 ID 는 한 줄만 남는다")
    void deduplicatesByBatchId() {
        AiBatchJob first = batchJobs.save(submitted(6L, "msgbatch_b"));
        entityManager.flush();

        AiBatchJob second = batchJobs.save(submitted(6L, "msgbatch_b"));
        entityManager.flush();

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(batchJobs.findBySkeletonId(6L)).hasSize(1);
    }

    /** 폴러가 끝나지 않은 것만 훑는다. 끝난 배치를 다시 집으면 반영이 두 번 일어난다. */
    @Test
    @DisplayName("완료한 배치는 폴러 목록에서 빠진다")
    void completedLeavesSubmittedList() {
        AiBatchJob saved = batchJobs.save(submitted(7L, "msgbatch_c"));
        entityManager.flush();
        assertThat(batchJobs.findSubmitted()).extracting(AiBatchJob::batchId).contains("msgbatch_c");

        AiBatchJob completed = batchJobs.save(saved.completed(LocalDateTime.of(2026, 8, 12, 10, 0)));
        entityManager.flush();
        entityManager.clear();

        assertThat(batchJobs.findSubmitted()).extracting(AiBatchJob::batchId).doesNotContain("msgbatch_c");
        assertThat(completed.completedAt()).isNotNull();
        // 상태를 고쳐도 제출 사실은 남는다 — merge 로 덮으면 반환 객체에서 사라진다.
        assertThat(completed.createdAt()).isNotNull();
        assertThat(completed.vendor()).isEqualTo(AiVendor.ANTHROPIC);
        assertThat(completed.model()).isEqualTo("claude-opus-5");
    }

    @Test
    @DisplayName("실패 사유가 저장된다")
    void savesFailureReason() {
        AiBatchJob saved = batchJobs.save(submitted(8L, "msgbatch_d"));
        entityManager.flush();

        AiBatchJob failed = batchJobs.save(saved.failed("결과 URL 이 없습니다", LocalDateTime.now()));
        entityManager.flush();
        entityManager.clear();

        assertThat(failed.status()).isEqualTo(AiBatchStatus.FAILED);
        assertThat(batchJobs.findBySkeletonId(8L)).singleElement().satisfies(job -> assertThat(job.failureReason())
                .isEqualTo("결과 URL 이 없습니다"));
    }
}
