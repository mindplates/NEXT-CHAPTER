package com.mindplates.nextchapter.adapter.out.persistence.chapter;

import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterRelationPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveChapterRelationPort;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChapterRelationPersistenceAdapter implements LoadChapterRelationPort, SaveChapterRelationPort {

    private final ChapterRelationJpaRepository relationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ChapterRelation> findBySkeletonId(Long skeletonId) {
        return relationRepository.findBySkeletonIdOrderByIdAsc(skeletonId).stream()
                .map(ChapterRelationPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countBySkeletonId(Long skeletonId) {
        return relationRepository.countBySkeletonId(skeletonId);
    }

    @Override
    @Transactional
    public List<ChapterRelation> saveAll(List<ChapterRelation> relations) {
        List<ChapterRelationJpaEntity> entities = relations.stream()
                .map(relation -> ChapterRelationJpaEntity.builder()
                        .id(relation.id())
                        .skeletonId(relation.skeletonId())
                        .fromChapterId(relation.fromChapterId())
                        .toChapterId(relation.toChapterId())
                        .relationType(relation.relationType())
                        .build())
                .toList();
        return relationRepository.saveAll(entities).stream()
                .map(ChapterRelationPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteBySkeletonId(Long skeletonId) {
        relationRepository.deleteBySkeletonId(skeletonId);
    }

    private static ChapterRelation toDomain(ChapterRelationJpaEntity entity) {
        return new ChapterRelation(
                entity.getId(),
                entity.getSkeletonId(),
                entity.getFromChapterId(),
                entity.getToChapterId(),
                entity.getRelationType(),
                entity.getCreatedAt());
    }
}
