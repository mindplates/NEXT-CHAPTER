package com.mindplates.nextchapter.adapter.out.persistence.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindplates.nextchapter.adapter.out.persistence.AbstractPostgresIT;
import com.mindplates.nextchapter.domain.generation.model.GenerationFailure;
import com.mindplates.nextchapter.domain.generation.model.GenerationFailureStatus;
import com.mindplates.nextchapter.domain.generation.model.GenerationStage;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 큐의 위험은 <b>중복</b>과 <b>기록 소실</b>에 몰려 있다. 같은 메시지가 두 번 소진되면 운영자가 몇 개가 실제
 * 문제인지 알 수 없고, 상태를 고칠 때 생성 시각이 사라지면 화면이 조용히 틀린 값을 낸다. 둘 다 목으로는
 * 드러나지 않는다.
 */
@Import(GenerationFailurePersistenceAdapter.class)
@DisplayName("생성 실패 큐 어댑터 (실제 PostgreSQL)")
class GenerationFailurePersistenceAdapterIT extends AbstractPostgresIT {

    @Autowired
    GenerationFailurePersistenceAdapter failures;

    @Autowired
    EntityManager entityManager;

    private GenerationFailure open(long offset, Long skeletonId) {
        return GenerationFailure.open(
                skeletonId,
                101L,
                GenerationStage.BODY,
                "skeleton.body.requested",
                3,
                offset,
                "{\"skeletonId\":" + skeletonId + ",\"chapterId\":101}",
                "com.example.VendorTimeout",
                "타임아웃",
                3);
    }

    @Test
    @DisplayName("좌표와 페이로드가 그대로 저장된다")
    void savesFailure() {
        GenerationFailure saved = failures.save(open(420L, 5L));
        entityManager.flush();

        assertThat(saved.id()).isNotNull();
        assertThat(saved.status()).isEqualTo(GenerationFailureStatus.OPEN);
        assertThat(saved.createdAt()).isNotNull();
        assertThat(failures.findByRecord("skeleton.body.requested", 3, 420L))
                .isPresent()
                .get()
                .satisfies(found -> {
                    assertThat(found.payload()).contains("chapterId");
                    assertThat(found.attempts()).isEqualTo(3);
                });
    }

    /** 파티션 재할당·오프셋 되돌림·컨슈머 재기동으로 같은 메시지가 두 번 소진될 수 있다. */
    @Test
    @DisplayName("같은 메시지 좌표는 한 줄만 남는다")
    void deduplicatesByRecordCoordinates() {
        GenerationFailure first = failures.save(open(500L, 5L));
        entityManager.flush();

        GenerationFailure second = failures.save(open(500L, 5L));
        entityManager.flush();

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(failures.findBySkeletonId(5L)).hasSize(1);
    }

    /**
     * 상태를 고칠 때 준영속 엔티티를 {@code merge} 하면 {@code created_at} 이 반환 객체에서 사라진다. 그
     * 반환값이 곧 큐 화면의 한 줄이므로 조용히 틀린 값이 화면에 나간다.
     */
    @Test
    @DisplayName("상태를 고쳐도 생성 시각이 남는다")
    void keepsCreatedAtOnStatusChange() {
        GenerationFailure saved = failures.save(open(600L, 7L));
        entityManager.flush();

        GenerationFailure resolved = failures.save(saved.resolved("admin", LocalDateTime.of(2026, 8, 12, 10, 0)));
        entityManager.flush();

        assertThat(resolved.createdAt()).isNotNull();
        assertThat(resolved.status()).isEqualTo(GenerationFailureStatus.RESOLVED);
        assertThat(resolved.resolvedBy()).isEqualTo("admin");
        assertThat(resolved.payload()).isEqualTo(saved.payload());
    }

    @Test
    @DisplayName("상태로 걸러 조회하고, null 이면 전체를 준다")
    void filtersByStatus() {
        failures.save(open(700L, 8L));
        GenerationFailure closed = failures.save(open(701L, 8L));
        entityManager.flush();
        failures.save(closed.resolved("admin", LocalDateTime.now()));
        entityManager.flush();
        entityManager.clear();

        assertThat(failures.findByStatus(GenerationFailureStatus.OPEN))
                .extracting(GenerationFailure::offset)
                .contains(700L)
                .doesNotContain(701L);
        assertThat(failures.findByStatus(null)).hasSizeGreaterThanOrEqualTo(2);
    }

    /** 읽을 수 없는 메시지가 조용히 사라지는 것이 이 큐를 만든 이유 그 자체다. */
    @Test
    @DisplayName("뼈대를 모르는 항목도 저장된다")
    void savesWithoutSkeleton() {
        GenerationFailure saved = failures.save(GenerationFailure.open(
                null,
                null,
                GenerationStage.BODY,
                "skeleton.body.requested",
                0,
                800L,
                "not-json",
                "JsonParse",
                null,
                3));
        entityManager.flush();

        assertThat(saved.id()).isNotNull();
        assertThat(saved.skeletonId()).isNull();
    }
}
