package com.mindplates.nextchapter.application.signal.service;

import com.mindplates.nextchapter.application.chapter.port.out.ChapterDocumentCachePort.CachedChapterDocument;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterVersionPort;
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
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.chapter.model.QuizDifficulty;
import com.mindplates.nextchapter.domain.signal.model.Signal;
import com.mindplates.nextchapter.domain.signal.model.SignalType;
import java.util.LinkedHashMap;
import java.util.Map;
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

    /** 클라이언트가 보내는 것 — 고른 답. */
    private static final String CHOICE_KEY = "choice";

    /** 서버가 채점해 채우는 것 — 오답률의 원천. */
    private static final String CORRECT_KEY = "correct";

    /**
     * 그 사용자가 본 문항의 난이도. 개인화가 문항을 고르므로, 이것 없이 오답률을 합치면 쉬운 문항을 본
     * 집단과 어려운 문항을 본 집단이 섞인다 — 집단 신호가 조용히 흐려진다.
     */
    private static final String DIFFICULTY_KEY = "difficulty";

    private final PublishedSkeletonGuard publishedSkeletonGuard;
    private final LoadChapterPort loadChapterPort;
    private final LoadChapterVersionPort loadChapterVersionPort;
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
                payloadFor(command)));

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
     * 퀴즈 응답은 <b>서버가 채점한다.</b>
     *
     * <p>클라이언트가 {@code correct} 를 보내게 하면 그 값은 위조할 수 있다. 오답률이 집단 루프의 핵심
     * 신호이므로, 위조 가능성 자체가 임계치 판정과 수정 전후 비교의 근거를 없앤다 — 그리고 그건 에러 없이
     * 일어난다. 그래서 클라이언트는 <b>고른 답</b>만 보내고 정답 대조는 여기서 한다.
     *
     * <p>채점에는 원천(버전 스냅샷)을 읽는다. 사용자에게 내려간 문서에는 정답이 없다.
     */
    private Map<String, Object> payloadFor(RecordSignalCommand command) {
        if (command.type() != SignalType.QUIZ_ANSWER) {
            return command.payload();
        }
        Object choice = command.payload() == null ? null : command.payload().get(CHOICE_KEY);
        if (choice == null) {
            throw new InvalidOperationException("퀴즈 응답에는 고른 답(choice)이 필요합니다.");
        }
        Block quiz = loadChapterVersionPort
                .find(command.chapterId(), command.chapterVersion())
                .flatMap(version -> version.body().findById(command.blockId()))
                .orElseThrow(() -> new InvalidOperationException(
                        "그 버전에 없는 블록입니다: v" + command.chapterVersion() + " " + command.blockId()));
        if (quiz.type() != BlockType.QUIZ) {
            throw new InvalidOperationException("퀴즈 블록이 아닙니다: " + command.blockId());
        }

        Map<String, Object> payload = new LinkedHashMap<>(command.payload());
        payload.put(CORRECT_KEY, matches(quiz.attributes().get(Block.ANSWER), choice));
        // 난이도를 함께 기록한다. 사용자마다 다른 문항을 보므로, 난이도 없이 오답률을 합치면 쉬운 문항을
        // 본 집단과 어려운 문항을 본 집단이 섞여 **집단 신호가 흐려진다.** M5 임계치 판정이 이 값으로 보정한다.
        payload.put(DIFFICULTY_KEY, QuizDifficulty.of(quiz).name());
        return payload;
    }

    /**
     * 정답 비교는 <b>문자열로</b> 한다. 정답이 JSON 에서 숫자로, 고른 답이 문자열로 올 수 있고(선택지
     * 인덱스), 그 차이로 정답을 오답으로 세면 오답률이 조용히 부풀려진다.
     */
    private static boolean matches(Object answer, Object choice) {
        return answer != null
                && String.valueOf(answer).trim().equals(String.valueOf(choice).trim());
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
