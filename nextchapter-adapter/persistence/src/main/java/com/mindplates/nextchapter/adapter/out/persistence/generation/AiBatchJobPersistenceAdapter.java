package com.mindplates.nextchapter.adapter.out.persistence.generation;

import com.mindplates.nextchapter.application.generation.port.out.LoadAiBatchJobPort;
import com.mindplates.nextchapter.application.generation.port.out.SaveAiBatchJobPort;
import com.mindplates.nextchapter.domain.generation.model.AiBatchJob;
import com.mindplates.nextchapter.domain.generation.model.AiBatchStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AiBatchJobPersistenceAdapter implements LoadAiBatchJobPort, SaveAiBatchJobPort {

    private final AiBatchJobJpaRepository batchJobRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AiBatchJob> findSubmitted() {
        return batchJobRepository.findByStatusOrderByCreatedAtAsc(AiBatchStatus.SUBMITTED).stream()
                .map(AiBatchJobPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiBatchJob> findBySkeletonId(Long skeletonId) {
        return batchJobRepository.findBySkeletonIdOrderByCreatedAtAsc(skeletonId).stream()
                .map(AiBatchJobPersistenceAdapter::toDomain)
                .toList();
    }

    /**
     * 새 잡이면 <b>같은 배치 ID 가 이미 있는지 먼저 본다.</b> 같은 배치를 두 줄로 두면 폴러가 두 번 반영하고,
     * 그러면 챕터마다 v2 가 쌓인다 — 수정이 아니라 중복이고, 이력에 "왜 바뀌었는지 설명할 수 없는 버전"이
     * 남는다. 유일 인덱스가 DB 에도 있지만 여기서 미리 찾으면 정상 재시도가 예외가 아니라 조회로 끝난다.
     *
     * <p>기존 잡은 관리 상태의 행을 찾아 상태만 고친다. 준영속 엔티티를 {@code merge} 하면 빌더가 채우지 않은
     * {@code created_at} 이 null 로 복사되어 반환 객체에서 사라지고, 그 값이 곧 운영자 화면의 한 줄이다.
     */
    @Override
    @Transactional
    public AiBatchJob save(AiBatchJob job) {
        if (job.id() != null) {
            return toDomain(updateStatus(job));
        }
        return batchJobRepository
                .findByBatchId(job.batchId())
                .map(AiBatchJobPersistenceAdapter::toDomain)
                .orElseGet(() -> toDomain(batchJobRepository.save(AiBatchJobJpaEntity.builder()
                        .skeletonId(job.skeletonId())
                        .stage(job.stage())
                        .vendor(job.vendor())
                        .model(job.model())
                        .batchId(job.batchId())
                        .status(job.status())
                        .itemCount(job.itemCount())
                        .failureReason(job.failureReason())
                        .completedAt(job.completedAt())
                        .build())));
    }

    private AiBatchJobJpaEntity updateStatus(AiBatchJob job) {
        AiBatchJobJpaEntity entity = batchJobRepository
                .findById(job.id())
                .orElseThrow(() -> new IllegalStateException("사라진 배치 잡입니다: " + job.id()));
        entity.setStatus(job.status());
        entity.setFailureReason(job.failureReason());
        entity.setCompletedAt(job.completedAt());
        return batchJobRepository.save(entity);
    }

    private static AiBatchJob toDomain(AiBatchJobJpaEntity entity) {
        return new AiBatchJob(
                entity.getId(),
                entity.getSkeletonId(),
                entity.getStage(),
                entity.getVendor(),
                entity.getModel(),
                entity.getBatchId(),
                entity.getStatus(),
                entity.getItemCount(),
                entity.getFailureReason(),
                entity.getCompletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
