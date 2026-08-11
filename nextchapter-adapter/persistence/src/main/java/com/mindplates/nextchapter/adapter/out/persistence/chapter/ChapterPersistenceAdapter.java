package com.mindplates.nextchapter.adapter.out.persistence.chapter;

import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveChapterPort;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChapterPersistenceAdapter implements LoadChapterPort, SaveChapterPort {

    private final ChapterJpaRepository chapterRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Chapter> findById(Long id) {
        return chapterRepository.findById(id).map(ChapterPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Chapter> findBySkeletonIdAndKey(Long skeletonId, String chapterKey) {
        return chapterRepository
                .findBySkeletonIdAndChapterKey(skeletonId, chapterKey)
                .map(ChapterPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Chapter> findBySkeletonId(Long skeletonId) {
        return chapterRepository.findBySkeletonIdOrderBySortOrderAscIdAsc(skeletonId).stream()
                .map(ChapterPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countBySkeletonId(Long skeletonId) {
        return chapterRepository.countBySkeletonId(skeletonId);
    }

    /** 본문 없음은 {@code current_version = 0} 이다. 별도 플래그를 두지 않은 이유가 여기서 드러난다. */
    @Override
    @Transactional(readOnly = true)
    public long countBySkeletonIdWithoutBody(Long skeletonId) {
        return chapterRepository.countBySkeletonIdAndCurrentVersion(skeletonId, 0);
    }

    @Override
    @Transactional
    public Chapter save(Chapter chapter) {
        return toDomain(chapterRepository.save(ChapterJpaEntity.builder()
                .id(chapter.id())
                .skeletonId(chapter.skeletonId())
                .chapterKey(chapter.chapterKey())
                .title(chapter.title())
                .summary(chapter.summary())
                .currentVersion(chapter.currentVersion())
                .blockIdSequence(chapter.blockIdSequence())
                .sortOrder(chapter.sortOrder())
                .build()));
    }

    private static Chapter toDomain(ChapterJpaEntity entity) {
        return new Chapter(
                entity.getId(),
                entity.getSkeletonId(),
                entity.getChapterKey(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getCurrentVersion(),
                entity.getBlockIdSequence(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
