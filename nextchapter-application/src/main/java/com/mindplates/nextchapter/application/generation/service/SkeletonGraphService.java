package com.mindplates.nextchapter.application.generation.service;

import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveChapterRelationPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveOutboxEventPort;
import com.mindplates.nextchapter.application.chapter.service.OutboxPayloads;
import com.mindplates.nextchapter.application.generation.port.in.GenerateSkeletonGraphUseCase;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelation;
import com.mindplates.nextchapter.domain.chapter.model.OutboxEvent;
import com.mindplates.nextchapter.domain.chapter.model.ProposedChapter;
import com.mindplates.nextchapter.domain.chapter.model.ProposedChapterGraph;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ① 그래프 생성 — 챕터 제목·요약과 챕터 간 관계를 만든다.
 *
 * <p>본문은 만들지 않는다. 이 단계의 산출물은 <b>구조</b>이고, 구조를 먼저 확정하는 이유는 개요(②)가
 * 챕터 간 중복·누락을 본문 생성 전에 잡을 수 있게 하기 위해서다.
 *
 * <p>재실행은 <b>덮어쓰기</b>다. 관계를 먼저 지우는 이유는 옛 간선이 새 그래프에 섞이면 순환이 생길 수
 * 있고, 그 순환은 생성 시점 검증을 이미 지나온 뒤라 걸리지 않기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SkeletonGraphService implements GenerateSkeletonGraphUseCase {

    private static final Logger log = LoggerFactory.getLogger(SkeletonGraphService.class);

    /** 그래프는 챕터 수십 개의 제목·요약과 관계를 한 응답에 담는다. 짧으면 응답이 잘려 파싱이 깨진다. */
    private static final int MAX_TOKENS = 8192;

    private final LoadSkeletonPort loadSkeletonPort;
    private final LoadCatalogTopicPort loadCatalogTopicPort;
    private final LoadCatalogFieldPort loadCatalogFieldPort;
    private final LoadCatalogDomainPort loadCatalogDomainPort;
    private final LoadChapterPort loadChapterPort;
    private final SaveChapterPort saveChapterPort;
    private final SaveChapterRelationPort saveChapterRelationPort;
    private final SaveOutboxEventPort saveOutboxEventPort;
    private final AiGateway aiGateway;

    @Override
    public int generate(Long skeletonId) {
        Skeleton skeleton =
                loadSkeletonPort.findById(skeletonId).orElseThrow(() -> new EntityNotFoundException("뼈대", skeletonId));
        CatalogTopic topic = loadCatalogTopicPort
                .findById(skeleton.topicId())
                .orElseThrow(() -> new EntityNotFoundException("주제", skeleton.topicId()));
        CatalogField field = loadCatalogFieldPort
                .findById(topic.fieldId())
                .orElseThrow(() -> new EntityNotFoundException("분야", topic.fieldId()));
        CatalogDomain domain = loadCatalogDomainPort
                .findById(field.domainId())
                .orElseThrow(() -> new EntityNotFoundException("도메인", field.domainId()));

        ProposedChapterGraph graph = SkeletonGraphPrompts.parse(aiGateway
                .complete(
                        AiStage.SKELETON_BODY,
                        SkeletonGraphPrompts.SYSTEM_PROMPT,
                        SkeletonGraphPrompts.prompt(topic.name(), topic.description(), field.name(), domain.name()),
                        MAX_TOKENS)
                .text());

        saveChapterRelationPort.deleteBySkeletonId(skeletonId);
        Map<String, Long> chapterIdsByKey = upsertChapters(skeletonId, graph.chapters());
        saveRelations(skeletonId, graph, chapterIdsByKey);

        log.info(
                "[그래프] 생성 완료 skeletonId={} chapters={} relations={} 진입점={}",
                skeletonId,
                graph.chapters().size(),
                graph.relations().size(),
                graph.entryPointKeys().size());
        return graph.chapters().size();
    }

    /**
     * 키가 같은 챕터는 <b>같은 챕터로 취급해 제목·요약만 갱신한다.</b>
     *
     * <p>지우고 다시 만들면 챕터 ID 가 갈리고, 사용자 레이어와 신호가 챕터 ID 기준으로 붙어 있으므로
     * 그 순간 학습 기록과 집단 신호가 끊긴다. 재생성이 그것을 지울 수는 없다.
     */
    private Map<String, Long> upsertChapters(Long skeletonId, List<ProposedChapter> proposed) {
        Map<String, Chapter> existing = new HashMap<>();
        loadChapterPort.findBySkeletonId(skeletonId).forEach(chapter -> existing.put(chapter.chapterKey(), chapter));

        Map<String, Long> idsByKey = new HashMap<>();
        for (ProposedChapter candidate : proposed) {
            Chapter saved = existing.containsKey(candidate.key())
                    ? saveChapterPort.save(
                            existing.get(candidate.key()).withOutline(candidate.title(), candidate.summary()))
                    : saveChapterPort.save(Chapter.create(
                            skeletonId,
                            candidate.key(),
                            candidate.title(),
                            candidate.summary(),
                            candidate.sortOrder()));
            idsByKey.put(candidate.key(), saved.id());
        }
        return idsByKey;
    }

    private void saveRelations(Long skeletonId, ProposedChapterGraph graph, Map<String, Long> chapterIdsByKey) {
        List<ChapterRelation> relations = graph.relations().stream()
                .map(relation -> ChapterRelation.of(
                        skeletonId,
                        chapterIdsByKey.get(relation.fromKey()),
                        chapterIdsByKey.get(relation.toKey()),
                        relation.relationType()))
                .toList();
        saveChapterRelationPort.saveAll(relations);

        // 관계와 같은 커밋에 outbox 행을 넣는다. Neo4j 는 파생이므로 여기서 직접 쓰지 않는다 —
        // 분산 트랜잭션이 없어 한쪽만 성공하면 본문 없는 노드나 그래프에 없는 챕터가 남는다.
        saveOutboxEventPort.append(OutboxEvent.chapterGraphPersisted(
                skeletonId, relations.size(), OutboxPayloads.chapterGraphPersisted(skeletonId, relations)));
    }
}
