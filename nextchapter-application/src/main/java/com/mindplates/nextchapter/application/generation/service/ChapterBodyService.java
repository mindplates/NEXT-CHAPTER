package com.mindplates.nextchapter.application.generation.service;

import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.chapter.port.in.RecordChapterBodyUseCase;
import com.mindplates.nextchapter.application.chapter.port.in.command.RecordChapterBodyCommand;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterOutlinePort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterRelationPort;
import com.mindplates.nextchapter.application.generation.port.in.GenerateChapterBodyUseCase;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterOutline;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelation;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersionSource;
import com.mindplates.nextchapter.domain.chapter.model.ProposedBlock;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ③ 본문 생성 — 챕터 하나의 블록 문서를 만든다.
 *
 * <p>챕터 단위라 <b>실패도 챕터 단위로 격리된다.</b> 메시지 키가 챕터 ID 이므로 실패한 챕터의 메시지만
 * 재시도되고, 같은 뼈대의 다른 챕터는 영향을 받지 않는다.
 *
 * <p>본문 기록은 {@link RecordChapterBodyUseCase} 를 지난다 — 블록 ID 승계와 버전 증가 규칙이 한 곳에만
 * 있어야 생성과 수정이 어긋나지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChapterBodyService implements GenerateChapterBodyUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChapterBodyService.class);

    /** 블록 문서 하나는 수 KB 다. 짧으면 뒤쪽 블록이 잘려 quiz·narration 이 빠진다. */
    private static final int MAX_TOKENS = 16384;

    private final LoadSkeletonPort loadSkeletonPort;
    private final LoadCatalogTopicPort loadCatalogTopicPort;
    private final LoadChapterPort loadChapterPort;
    private final LoadChapterOutlinePort loadChapterOutlinePort;
    private final LoadChapterRelationPort loadChapterRelationPort;
    private final RecordChapterBodyUseCase recordChapterBodyUseCase;
    private final AiGateway aiGateway;

    /**
     * 이미 본문이 있으면 만들지 않는다.
     *
     * <p>Kafka 는 at-least-once 이므로 같은 본문 생성 메시지를 두 번 받는 것이 정상이다. 그때 그냥
     * 생성하면 v2 가 쌓이는데, 그건 <b>수정이 아닌 중복</b>이고 이력에 "왜 바뀌었는지 설명할 수 없는
     * 버전"을 남긴다. 수정은 M5 의 명시적 경로로만 들어온다.
     */
    @Override
    public boolean generate(Long skeletonId, Long chapterId) {
        Chapter chapter =
                loadChapterPort.findById(chapterId).orElseThrow(() -> new EntityNotFoundException("챕터", chapterId));
        if (chapter.hasBody()) {
            log.info("[본문] 이미 본문이 있어 건너뛴다 chapterId={} version={}", chapterId, chapter.currentVersion());
            return false;
        }

        ChapterOutline outline = loadChapterOutlinePort
                .findByChapterId(chapterId)
                .orElseThrow(() -> new InvalidOperationException("개요가 없어 본문을 생성할 수 없습니다. chapterId=" + chapterId));

        Skeleton skeleton =
                loadSkeletonPort.findById(skeletonId).orElseThrow(() -> new EntityNotFoundException("뼈대", skeletonId));
        CatalogTopic topic = loadCatalogTopicPort
                .findById(skeleton.topicId())
                .orElseThrow(() -> new EntityNotFoundException("주제", skeleton.topicId()));

        List<Chapter> siblings = loadChapterPort.findBySkeletonId(skeletonId);
        List<ProposedBlock> blocks = ChapterBodyPrompts.parse(aiGateway
                .complete(
                        AiStage.SKELETON_BODY,
                        ChapterBodyPrompts.SYSTEM_PROMPT,
                        ChapterBodyPrompts.prompt(
                                topic.name(),
                                chapter.title(),
                                chapter.summary(),
                                outline.points(),
                                prerequisiteTitles(skeletonId, chapterId, siblings),
                                siblingTitles(chapterId, siblings)),
                        MAX_TOKENS)
                .text());

        requireSources(chapterId, blocks);

        recordChapterBodyUseCase.record(
                chapterId,
                new RecordChapterBodyCommand(blocks, ChapterVersionSource.GENERATED, null, "generation-pipeline"));
        log.info("[본문] 생성 완료 chapterId={} blocks={}", chapterId, blocks.size());
        return true;
    }

    /**
     * 출처가 하나도 없으면 실패로 다룬다.
     *
     * <p>검증 패스가 검사할 대상이 없다는 뜻이고, 그 상태는 "검증을 통과했다"와 구분되지 않는다. 재시도
     * 비용이 나중에 검증할 수 없는 콘텐츠를 안고 가는 비용보다 싸다.
     */
    private static void requireSources(Long chapterId, List<ProposedBlock> blocks) {
        if (!ChapterBodyPrompts.hasAnySource(blocks)) {
            throw new ExternalApiException("본문에 출처가 하나도 없습니다. chapterId=" + chapterId);
        }
    }

    /** 선행 챕터의 제목. 이미 다뤄진 내용을 다시 설명하지 않게 한다. */
    private List<String> prerequisiteTitles(Long skeletonId, Long chapterId, List<Chapter> siblings) {
        Map<Long, String> titlesById = siblings.stream().collect(Collectors.toMap(Chapter::id, Chapter::title));
        return loadChapterRelationPort.findBySkeletonId(skeletonId).stream()
                .filter(ChapterRelation::isPrerequisite)
                .filter(relation -> relation.toChapterId().equals(chapterId))
                .map(relation -> titlesById.get(relation.fromChapterId()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** 다른 챕터의 제목. 경계를 알려 주지 않으면 한 챕터가 다른 챕터의 범위를 먹는다. */
    private static List<String> siblingTitles(Long chapterId, List<Chapter> siblings) {
        return siblings.stream()
                .filter(chapter -> !chapter.id().equals(chapterId))
                .map(Chapter::title)
                .toList();
    }
}
