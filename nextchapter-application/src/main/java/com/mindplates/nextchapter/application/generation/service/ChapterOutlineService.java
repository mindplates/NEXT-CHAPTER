package com.mindplates.nextchapter.application.generation.service;

import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterRelationPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveChapterOutlinePort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveOutlineFindingPort;
import com.mindplates.nextchapter.application.generation.port.in.GenerateChapterOutlinesUseCase;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterOutline;
import com.mindplates.nextchapter.domain.chapter.model.OutlineFinding;
import com.mindplates.nextchapter.domain.chapter.model.ProposedOutlineSet;
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
 * ② 개요 생성 — 챕터별 개요를 쓰고 챕터 간 중복·누락을 보고한다.
 *
 * <p>이 단계를 따로 둔 대가는 호출 횟수와 대기시간이다. 그걸 감수하는 이유는 중복·누락을 <b>본문 생성
 * 전에</b> 잡는 편이 훨씬 싸기 때문이다 — 본문을 다 쓴 뒤 겹친다는 것을 알면 그 챕터의 본문 생성비와
 * 파생 자산비가 모두 버려진다.
 *
 * <p>소견은 <b>본문 생성을 막지 않는다.</b> 겹침 판정은 어림이고, 오탐 하나가 파이프라인을 세우면 사전
 * 생성의 이점이 사라진다. 막는 것은 "개요가 없는 챕터"뿐이다 — 그건 어림이 아니라 사실이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChapterOutlineService implements GenerateChapterOutlinesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChapterOutlineService.class);

    /** 챕터 수십 개의 개요를 한 응답에 담는다. 짧으면 뒤쪽 챕터가 잘려 커버리지 검증에서 걸린다. */
    private static final int MAX_TOKENS = 16384;

    private final LoadSkeletonPort loadSkeletonPort;
    private final LoadCatalogTopicPort loadCatalogTopicPort;
    private final LoadChapterPort loadChapterPort;
    private final LoadChapterRelationPort loadChapterRelationPort;
    private final SaveChapterOutlinePort saveChapterOutlinePort;
    private final SaveOutlineFindingPort saveOutlineFindingPort;
    private final AiGateway aiGateway;

    @Override
    public int generate(Long skeletonId) {
        Skeleton skeleton =
                loadSkeletonPort.findById(skeletonId).orElseThrow(() -> new EntityNotFoundException("뼈대", skeletonId));
        CatalogTopic topic = loadCatalogTopicPort
                .findById(skeleton.topicId())
                .orElseThrow(() -> new EntityNotFoundException("주제", skeleton.topicId()));

        List<Chapter> chapters = loadChapterPort.findBySkeletonId(skeletonId);
        if (chapters.isEmpty()) {
            // 그래프 단계가 아무것도 만들지 못한 상태다. 개요를 쓸 대상이 없으므로 다음 단계로
            // 넘기면 완료 판정이 즉시 참이 되어 빈 뼈대가 published 로 간다.
            throw new InvalidOperationException("그래프 단계의 챕터가 없어 개요를 생성할 수 없습니다. skeletonId=" + skeletonId);
        }

        ProposedOutlineSet proposed = ChapterOutlinePrompts.parse(aiGateway
                .complete(
                        AiStage.SKELETON_BODY,
                        skeletonId,
                        ChapterOutlinePrompts.SYSTEM_PROMPT,
                        ChapterOutlinePrompts.prompt(
                                topic.name(), chapters, loadChapterRelationPort.findBySkeletonId(skeletonId)),
                        MAX_TOKENS)
                .text());

        Map<String, Long> chapterIdsByKey =
                chapters.stream().collect(Collectors.toMap(Chapter::chapterKey, Chapter::id));
        proposed.requireCoverageOf(chapterIdsByKey.keySet());

        List<ChapterOutline> outlines = proposed.outlines().stream()
                .map(outline -> ChapterOutline.of(chapterIdsByKey.get(outline.chapterKey()), outline.points()))
                .toList();
        saveChapterOutlinePort.saveAll(outlines);

        // 옛 소견을 지운다 — 이미 고친 겹침이 검수 화면에 남아 있으면 판단을 흐린다.
        saveOutlineFindingPort.deleteBySkeletonId(skeletonId);
        List<OutlineFinding> findings = proposed.findings().stream()
                .map(finding ->
                        OutlineFinding.of(skeletonId, finding.findingType(), finding.chapterKeys(), finding.detail()))
                .toList();
        saveOutlineFindingPort.saveAll(findings);

        if (!findings.isEmpty()) {
            log.warn("[개요] 중복·누락 소견 {}건 skeletonId={} — 본문 생성은 계속한다", findings.size(), skeletonId);
        }
        log.info("[개요] 생성 완료 skeletonId={} chapters={} findings={}", skeletonId, outlines.size(), findings.size());
        return outlines.size();
    }
}
