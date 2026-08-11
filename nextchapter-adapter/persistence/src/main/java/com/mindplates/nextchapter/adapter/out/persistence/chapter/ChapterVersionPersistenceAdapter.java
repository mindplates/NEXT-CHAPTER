package com.mindplates.nextchapter.adapter.out.persistence.chapter;

import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterVersionPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveChapterVersionPort;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersion;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChapterVersionPersistenceAdapter implements LoadChapterVersionPort, SaveChapterVersionPort {

    private final ChapterVersionJpaRepository versionRepository;
    private final BlockDocumentJsonMapper jsonMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<ChapterVersion> find(Long chapterId, int version) {
        return versionRepository.findByChapterIdAndVersion(chapterId, version).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChapterVersion> findLatest(Long chapterId) {
        return versionRepository
                .findFirstByChapterIdOrderByVersionDesc(chapterId)
                .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChapterVersion> findAllByChapterId(Long chapterId) {
        return versionRepository.findByChapterIdOrderByVersionDesc(chapterId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public ChapterVersion save(ChapterVersion version) {
        return toDomain(versionRepository.save(ChapterVersionJpaEntity.builder()
                .id(version.id())
                .chapterId(version.chapterId())
                .version(version.version())
                .body(jsonMapper.toJson(version.body()))
                .changeSummary(version.changeSummary())
                .source(version.source())
                .createdBy(version.createdBy())
                .createdAt(version.createdAt())
                .build()));
    }

    private ChapterVersion toDomain(ChapterVersionJpaEntity entity) {
        return new ChapterVersion(
                entity.getId(),
                entity.getChapterId(),
                entity.getVersion(),
                jsonMapper.toDocument(entity.getBody()),
                entity.getChangeSummary(),
                entity.getSource(),
                entity.getCreatedBy(),
                entity.getCreatedAt());
    }
}
