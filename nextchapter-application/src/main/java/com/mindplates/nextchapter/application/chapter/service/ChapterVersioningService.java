package com.mindplates.nextchapter.application.chapter.service;

import com.mindplates.nextchapter.application.chapter.port.in.GetChapterUseCase;
import com.mindplates.nextchapter.application.chapter.port.in.RecordChapterBodyUseCase;
import com.mindplates.nextchapter.application.chapter.port.in.command.RecordChapterBodyCommand;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterVersionPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveChapterVersionPort;
import com.mindplates.nextchapter.application.chapter.view.ChapterBodyView;
import com.mindplates.nextchapter.application.chapter.view.ChapterVersionSummaryView;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.BlockDocument;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersion;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 본문 기록과 버전 조회.
 *
 * <p>기록이 한 트랜잭션이어야 하는 이유는 셋이 함께 움직이기 때문이다 — 새 버전 스냅샷, 챕터의
 * {@code currentVersion}, 그리고 블록 ID 카운터. 하나만 커밋되면 다음 발급이 이미 쓰인 ID 를 내주거나
 * 사용자가 존재하지 않는 버전을 보게 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChapterVersioningService implements RecordChapterBodyUseCase, GetChapterUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChapterVersioningService.class);

    private final LoadChapterPort loadChapterPort;
    private final SaveChapterPort saveChapterPort;
    private final LoadChapterVersionPort loadChapterVersionPort;
    private final SaveChapterVersionPort saveChapterVersionPort;

    @Override
    public ChapterVersion record(Long chapterId, RecordChapterBodyCommand command) {
        Chapter chapter =
                loadChapterPort.findById(chapterId).orElseThrow(() -> new EntityNotFoundException("챕터", chapterId));
        BlockDocument previous = loadChapterVersionPort
                .findLatest(chapterId)
                .map(ChapterVersion::body)
                .orElse(null);

        BlockDocument.Succession succession =
                BlockDocument.succeed(command.blocks(), previous, chapter.nextBlockSequence());
        int nextVersion = chapter.currentVersion() + 1;

        ChapterVersion saved = saveChapterVersionPort.save(new ChapterVersion(
                null,
                chapterId,
                nextVersion,
                succession.document(),
                command.changeSummary(),
                command.source(),
                command.actor(),
                null));
        // 카운터는 승계가 소비한 값으로 올라간다. 되돌리지 않는 것이 ID 재사용을 막는 장치다.
        saveChapterPort.save(chapter.withNewVersion(nextVersion, succession.nextSequence() - 1));

        log.info(
                "[챕터] 본문 기록 chapterId={} version={} blocks={} source={}",
                chapterId,
                nextVersion,
                succession.document().blocks().size(),
                command.source());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public ChapterBodyView currentBody(Long chapterId) {
        Chapter chapter =
                loadChapterPort.findById(chapterId).orElseThrow(() -> new EntityNotFoundException("챕터", chapterId));
        ChapterVersion latest = loadChapterVersionPort
                .findLatest(chapterId)
                .orElseThrow(() -> new EntityNotFoundException("챕터 본문", chapterId));
        return toView(chapter, latest);
    }

    @Override
    @Transactional(readOnly = true)
    public ChapterBodyView bodyAt(Long chapterId, int version) {
        Chapter chapter =
                loadChapterPort.findById(chapterId).orElseThrow(() -> new EntityNotFoundException("챕터", chapterId));
        ChapterVersion found = loadChapterVersionPort
                .find(chapterId, version)
                .orElseThrow(() -> new EntityNotFoundException("챕터 버전", chapterId + ":v" + version));
        return toView(chapter, found);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChapterVersionSummaryView> history(Long chapterId) {
        return loadChapterVersionPort.findAllByChapterId(chapterId).stream()
                .map(version -> new ChapterVersionSummaryView(
                        version.version(),
                        version.source(),
                        version.changeSummary(),
                        version.createdBy(),
                        version.body().blocks().size(),
                        version.createdAt()))
                .toList();
    }

    private static ChapterBodyView toView(Chapter chapter, ChapterVersion version) {
        return new ChapterBodyView(
                chapter.id(),
                chapter.chapterKey(),
                chapter.title(),
                chapter.summary(),
                version.version(),
                // 웹 렌더러가 보는 블록만 내린다 — narration 은 영상·자막의 원천이다.
                version.body().webBlocks(),
                version.isRevision(),
                version.changeSummary(),
                version.createdAt());
    }

    /** 진행률·완료 판정이 쓰는 값. published 전환 조건("전 챕터 완료")의 재료다. */
    @Transactional(readOnly = true)
    public Optional<Chapter> findByKey(Long skeletonId, String chapterKey) {
        return loadChapterPort.findBySkeletonIdAndKey(skeletonId, chapterKey);
    }
}
