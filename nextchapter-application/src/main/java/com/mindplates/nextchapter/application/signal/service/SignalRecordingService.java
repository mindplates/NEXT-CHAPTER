package com.mindplates.nextchapter.application.signal.service;

import com.mindplates.nextchapter.application.chapter.port.out.ChapterDocumentCachePort.CachedChapterDocument;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.service.SharedChapterDocuments;
import com.mindplates.nextchapter.application.signal.port.in.RecordSignalUseCase;
import com.mindplates.nextchapter.application.signal.port.in.command.RecordSignalCommand;
import com.mindplates.nextchapter.application.signal.port.out.SaveSignalPort;
import com.mindplates.nextchapter.application.signal.view.SignalView;
import com.mindplates.nextchapter.application.skeleton.service.PublishedSkeletonGuard;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockIds;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.signal.model.Signal;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신호 기록 — <b>개인 루프의 동기 경로</b>.
 *
 * <p>같은 트랜잭션에서 Postgres 에 쓰는 이유는 "방금 답한 결과가 다음 챕터에 반영되지 않는" 타이밍
 * 문제를 만들지 않기 위해서다. 집단 루프로의 발행은 {@link SignalPublishService} 가 스윕으로 한다 —
 * 이 테이블이 곧 그 outbox다.
 *
 * <p><b>blockId 가 그 버전에 실재하는지 확인한다.</b> 없는 블록에 붙은 신호는 저장돼도 어느 지점의
 * 집계에도 기여하지 않으면서 건수만 늘린다. 그 상태는 에러 없이 "질문은 많은데 고칠 지점을 찾을 수
 * 없다"로 나타난다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SignalRecordingService implements RecordSignalUseCase {

    private static final Logger log = LoggerFactory.getLogger(SignalRecordingService.class);

    private final PublishedSkeletonGuard publishedSkeletonGuard;
    private final LoadChapterPort loadChapterPort;
    private final SharedChapterDocuments sharedChapterDocuments;
    private final SaveSignalPort saveSignalPort;

    @Override
    public SignalView record(Long userId, RecordSignalCommand command) {
        Chapter chapter = loadChapterPort
                .findById(command.chapterId())
                .orElseThrow(() -> new EntityNotFoundException("챕터", command.chapterId()));
        publishedSkeletonGuard.requireById(chapter.skeletonId());

        DeliveryFormat format = command.format() == null ? DeliveryFormat.WEB : command.format();
        requireBlockExists(command, format);

        Signal saved = saveSignalPort.save(Signal.of(
                userId,
                chapter.id(),
                command.chapterVersion(),
                command.blockId(),
                format,
                command.type(),
                command.payload()));

        log.debug(
                "[신호] {} chapterId={} v{} blockId={} format={}",
                saved.type(),
                saved.chapterId(),
                saved.chapterVersion(),
                saved.blockId(),
                saved.format());
        return SignalView.from(saved);
    }

    /**
     * 공용 본문 블록만 확인한다.
     *
     * <p>보충 블록({@code s{n}})은 그 사용자의 레이어에만 있으므로 공용 문서에서 찾을 수 없다. 그걸
     * 거절하면 개인화 블록에 대한 질문을 받을 수 없고, 통과시키되 <b>구분해서</b> 저장하면 집단 집계가
     * 공용 블록만 겹쳐 볼 수 있다.
     *
     * <p>소비한 버전으로 확인하는 것이 요점이다. 최신 버전으로 확인하면 읽는 사이에 수정이 반영된
     * 사용자의 정상 신호가 거절된다.
     */
    private void requireBlockExists(RecordSignalCommand command, DeliveryFormat format) {
        String blockId = command.blockId();
        if (blockId == null || BlockIds.isSupplement(blockId)) {
            return;
        }
        CachedChapterDocument document =
                sharedChapterDocuments.at(command.chapterId(), command.chapterVersion(), format);
        boolean exists = document.blocks().stream().map(Block::id).anyMatch(blockId::equals);
        if (!exists) {
            throw new InvalidOperationException("그 버전에 없는 블록입니다: v" + command.chapterVersion() + " " + blockId);
        }
    }
}
