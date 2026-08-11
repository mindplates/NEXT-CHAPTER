package com.mindplates.nextchapter.adapter.out.persistence.skeleton;

import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.application.skeleton.port.out.SaveSkeletonPort;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SkeletonPersistenceAdapter implements LoadSkeletonPort, SaveSkeletonPort {

    private final SkeletonJpaRepository skeletonRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Skeleton> findById(Long id) {
        return skeletonRepository.findById(id).map(SkeletonPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Skeleton> findByTopicId(Long topicId) {
        return skeletonRepository.findByTopicId(topicId).map(SkeletonPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findTopicIdsWithSkeleton() {
        return skeletonRepository.findAllTopicIds();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Skeleton> findByStatus(SkeletonStatus status) {
        return skeletonRepository.findByStatusOrderByIdAsc(status).stream()
                .map(SkeletonPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public Skeleton save(Skeleton skeleton) {
        return toDomain(skeletonRepository.save(SkeletonJpaEntity.builder()
                .id(skeleton.id())
                .topicId(skeleton.topicId())
                .status(skeleton.status())
                .build()));
    }

    private static Skeleton toDomain(SkeletonJpaEntity entity) {
        return new Skeleton(
                entity.getId(), entity.getTopicId(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
