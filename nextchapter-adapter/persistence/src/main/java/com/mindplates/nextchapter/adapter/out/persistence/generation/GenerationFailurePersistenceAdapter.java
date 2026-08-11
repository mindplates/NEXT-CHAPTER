package com.mindplates.nextchapter.adapter.out.persistence.generation;

import com.mindplates.nextchapter.application.generation.port.out.LoadGenerationFailurePort;
import com.mindplates.nextchapter.application.generation.port.out.SaveGenerationFailurePort;
import com.mindplates.nextchapter.domain.generation.model.GenerationFailure;
import com.mindplates.nextchapter.domain.generation.model.GenerationFailureStatus;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GenerationFailurePersistenceAdapter implements LoadGenerationFailurePort, SaveGenerationFailurePort {

    private final GenerationFailureJpaRepository failureRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<GenerationFailure> findById(Long id) {
        return failureRepository.findById(id).map(GenerationFailurePersistenceAdapter::toDomain);
    }

    /** {@code status} 가 null 이면 전체다. 큐 화면이 "처리된 것까지 보기"를 필요로 한다. */
    @Override
    @Transactional(readOnly = true)
    public List<GenerationFailure> findByStatus(GenerationFailureStatus status) {
        List<GenerationFailureJpaEntity> found = status == null
                ? failureRepository.findAllByOrderByCreatedAtAsc()
                : failureRepository.findByStatusOrderByCreatedAtAsc(status);
        return found.stream().map(GenerationFailurePersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GenerationFailure> findBySkeletonId(Long skeletonId) {
        return failureRepository.findBySkeletonIdOrderByCreatedAtAsc(skeletonId).stream()
                .map(GenerationFailurePersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GenerationFailure> findByRecord(String topic, int partition, long offset) {
        return failureRepository
                .findByTopicAndPartitionAndOffset(topic, partition, offset)
                .map(GenerationFailurePersistenceAdapter::toDomain);
    }

    /**
     * 새 항목이면 <b>같은 메시지 좌표의 기존 항목을 먼저 찾는다.</b>
     *
     * <p>같은 메시지가 두 번 소진될 수 있다 — 파티션 재할당, 오프셋 되돌림, 컨슈머 재기동. 그때 큐에 같은
     * 항목이 여러 줄 쌓이면 운영자가 몇 개가 실제 문제인지 알 수 없다. 유일 인덱스가 DB 에도 있지만, 여기서
     * 미리 찾아 두면 정상 재소진이 예외가 아니라 조회로 끝난다.
     */
    @Override
    @Transactional
    public GenerationFailure save(GenerationFailure failure) {
        if (failure.id() != null) {
            return toDomain(updateStatus(failure));
        }
        Optional<GenerationFailureJpaEntity> existing = failureRepository.findByTopicAndPartitionAndOffset(
                failure.topic(), failure.partition(), failure.offset());
        if (existing.isPresent()) {
            return toDomain(existing.get());
        }
        return toDomain(failureRepository.save(GenerationFailureJpaEntity.builder()
                .id(failure.id())
                .skeletonId(failure.skeletonId())
                .chapterId(failure.chapterId())
                .stage(failure.stage())
                .topic(failure.topic())
                .partition(failure.partition())
                .offset(failure.offset())
                .payload(failure.payload())
                .errorType(failure.errorType())
                .errorMessage(failure.errorMessage())
                .attempts(failure.attempts())
                .status(failure.status())
                .resolvedBy(failure.resolvedBy())
                .resolvedAt(failure.resolvedAt())
                .build()));
    }

    /**
     * 기존 항목은 <b>관리 상태의 행을 찾아 상태만 고친다.</b> 준영속 엔티티를 {@code merge} 하면 빌더가
     * 채우지 않은 {@code created_at} 이 null 로 복사되어 반환 객체에서 사라진다 — 컬럼은
     * {@code updatable = false} 라 DB 에는 남지만, 그 반환값이 곧 큐 화면의 한 줄이므로 조용히 틀린 값이
     * 화면에 나간다.
     *
     * <p>고칠 수 있는 것은 상태와 처리자뿐이다. 메시지 좌표·페이로드·오류는 사실의 기록이므로 바뀌지
     * 않는다(엔티티에 {@code updatable = false}).
     */
    private GenerationFailureJpaEntity updateStatus(GenerationFailure failure) {
        GenerationFailureJpaEntity entity = failureRepository
                .findById(failure.id())
                .orElseThrow(() -> new IllegalStateException("사라진 실패 항목입니다: " + failure.id()));
        entity.setStatus(failure.status());
        entity.setResolvedBy(failure.resolvedBy());
        entity.setResolvedAt(failure.resolvedAt());
        return failureRepository.save(entity);
    }

    private static GenerationFailure toDomain(GenerationFailureJpaEntity entity) {
        return new GenerationFailure(
                entity.getId(),
                entity.getSkeletonId(),
                entity.getChapterId(),
                entity.getStage(),
                entity.getTopic(),
                entity.getPartition(),
                entity.getOffset(),
                entity.getPayload(),
                entity.getErrorType(),
                entity.getErrorMessage(),
                entity.getAttempts(),
                entity.getStatus(),
                entity.getResolvedBy(),
                entity.getResolvedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
