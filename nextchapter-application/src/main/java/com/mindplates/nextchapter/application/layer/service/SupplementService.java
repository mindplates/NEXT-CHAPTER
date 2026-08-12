package com.mindplates.nextchapter.application.layer.service;

import com.mindplates.nextchapter.application.chapter.port.out.ChapterDocumentCachePort.CachedChapterDocument;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.service.SharedChapterDocuments;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionResult;
import com.mindplates.nextchapter.application.generation.service.AiGateway;
import com.mindplates.nextchapter.application.layer.port.in.RequestSupplementUseCase;
import com.mindplates.nextchapter.application.layer.port.out.LoadSupplementBlockPort;
import com.mindplates.nextchapter.application.layer.port.out.SaveSupplementBlockPort;
import com.mindplates.nextchapter.application.layer.port.out.SaveSupplementBlockPort.NewSupplement;
import com.mindplates.nextchapter.application.layer.view.SupplementView;
import com.mindplates.nextchapter.application.signal.port.out.LoadSignalPort;
import com.mindplates.nextchapter.application.skeleton.service.PublishedSkeletonGuard;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.common.text.Strings;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockIds;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보충 블록 생성 — 개인화가 <b>본문을 고치지 않고</b> 일어나는 지점.
 *
 * <p>만드는 것은 새 블록이고 저장되는 것도 새 행이다. 본문 블록을 가리켜 바꾸는 경로가 이 서비스에도,
 * 테이블에도, 합성 함수에도 없다 — 규칙으로 두면 언젠가 깨지므로 세 층에서 구조로 막았다.
 *
 * <p>같은 지점을 다시 요청하면 이미 만든 것을 돌려준다. 그것이 "조회마다 생성하면 비용이 사용자 수 × 조회
 * 수로 늘어난다"의 대응이고, DB 의 UNIQUE 가 그 방어선의 마지막 층이다.
 *
 * <p>AI 호출은 {@link AiGateway} 를 지난다 — 예산 상한 검사가 그 한 곳에만 있기 때문이다. 개인화 호출을
 * 직접 벤더에 보내면 뼈대당·일일 상한 밖으로 새는 경로가 하나 생긴다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SupplementService implements RequestSupplementUseCase {

    private static final Logger log = LoggerFactory.getLogger(SupplementService.class);

    private final PublishedSkeletonGuard publishedSkeletonGuard;
    private final LoadChapterPort loadChapterPort;
    private final SharedChapterDocuments sharedChapterDocuments;
    private final LoadSignalPort loadSignalPort;
    private final LoadSupplementBlockPort loadSupplementBlockPort;
    private final SaveSupplementBlockPort saveSupplementBlockPort;
    private final AiGateway aiGateway;

    @Override
    public SupplementView request(Long userId, Long chapterId, String anchorBlockId) {
        if (!BlockIds.isBody(anchorBlockId)) {
            // 보충 블록에 보충을 붙이면 그 순서가 정의되지 않는다.
            throw new InvalidOperationException("앵커는 공용 본문 블록이어야 합니다: " + anchorBlockId);
        }
        Chapter chapter =
                loadChapterPort.findById(chapterId).orElseThrow(() -> new EntityNotFoundException("챕터", chapterId));
        publishedSkeletonGuard.requireById(chapter.skeletonId());

        Optional<com.mindplates.nextchapter.domain.layer.model.SupplementBlock> existing =
                loadSupplementBlockPort.findByAnchor(userId, chapterId, anchorBlockId);
        if (existing.isPresent()) {
            // 새로고침 한 번이 LLM 호출 한 번이 되지 않게 한다.
            return SupplementView.of(existing.get(), true);
        }

        CachedChapterDocument document = sharedChapterDocuments.current(chapter, DeliveryFormat.WEB);
        Block anchor = document.blocks().stream()
                .filter(block -> block.id().equals(anchorBlockId))
                .findFirst()
                .orElseThrow(() -> new InvalidOperationException("그 버전에 없는 블록입니다: " + anchorBlockId));

        String text = generate(chapter, document, anchor, userId, chapterId);
        com.mindplates.nextchapter.domain.layer.model.SupplementBlock saved =
                saveSupplementBlockPort.create(new NewSupplement(
                        userId, chapterId, document.version(), anchorBlockId, BlockType.PARAGRAPH, text, Map.of()));

        log.info(
                "[보충] 생성 userId={} chapterId={} v{} anchor={} blockId={}",
                userId,
                chapterId,
                document.version(),
                anchorBlockId,
                saved.block().id());
        return SupplementView.of(saved, false);
    }

    /**
     * 빈 응답을 통과시키지 않는다. 빈 보충 블록을 저장하면 사용자는 "설명을 만들었습니다"라는 말과 빈 칸을
     * 함께 보게 되고, 그 상태는 다시 요청해도 이미 있는 것으로 판정돼 <b>영구히 빈칸</b>이 된다.
     */
    private String generate(
            Chapter chapter, CachedChapterDocument document, Block anchor, Long userId, Long chapterId) {
        LlmCompletionResult result = aiGateway.complete(
                AiStage.PERSONALIZATION,
                // 뼈대당 상한에 포함시킨다 — 이 비용도 그 뼈대가 만든 것이다.
                chapter.skeletonId(),
                SupplementPrompts.system(),
                SupplementPrompts.user(
                        chapter.title(),
                        document.blocks(),
                        anchor,
                        loadSignalPort.findByUserAndChapter(userId, chapterId)),
                SupplementPrompts.MAX_TOKENS);

        String text = Strings.trimToNull(result.text());
        if (text == null) {
            throw new ExternalApiException("보충 설명이 비어 있습니다.");
        }
        return text;
    }
}
