package com.mindplates.nextchapter.application.chapter.service;

import com.mindplates.nextchapter.application.chapter.port.in.ExploreChapterGraphUseCase;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterGraphPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.view.ChapterNeighborhoodView;
import com.mindplates.nextchapter.application.chapter.view.ChapterNodeView;
import com.mindplates.nextchapter.application.chapter.view.SkeletonEntryView;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자의 그래프 탐색.
 *
 * <p><b>published 검사가 이 서비스의 존재 이유다.</b> 생성 중인 뼈대의 그래프는 간선이 아직 덜 들어와
 * 있을 수 있고, 그 상태를 그대로 내리면 사용자에게 "관련 챕터 없음"으로 보인다 — 에러가 아니라 잘못된
 * 콘텐츠다. published 는 "전 챕터 완료 + 미발행 outbox 0건"이므로, 그 조건을 통과한 뼈대의 그래프는
 * 완결돼 있다.
 *
 * <p>공개 여부를 <b>Postgres</b>에 묻는다. Neo4j 는 파생 저장소라 반영이 뒤처질 수 있고, 그 지연이
 * 접근 판정에 섞이면 "잠깐 열렸다 닫히는" 상태가 만들어진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChapterGraphExploreService implements ExploreChapterGraphUseCase {

    private final LoadSkeletonPort loadSkeletonPort;
    private final LoadChapterPort loadChapterPort;
    private final LoadChapterGraphPort loadChapterGraphPort;

    @Override
    public SkeletonEntryView entryPointsByTopicId(Long topicId) {
        return entryPoints(requirePublished(loadSkeletonPort.findByTopicId(topicId), "공개된 뼈대(주제)", topicId));
    }

    @Override
    public SkeletonEntryView entryPointsBySkeletonId(Long skeletonId) {
        return entryPoints(requirePublished(loadSkeletonPort.findById(skeletonId), "공개된 뼈대", skeletonId));
    }

    @Override
    public ChapterNeighborhoodView neighbors(Long chapterId, int depth) {
        Chapter chapter =
                loadChapterPort.findById(chapterId).orElseThrow(() -> new EntityNotFoundException("챕터", chapterId));
        requirePublished(loadSkeletonPort.findById(chapter.skeletonId()), "공개된 뼈대", chapter.skeletonId());

        LoadChapterGraphPort.Neighborhood neighborhood = loadChapterGraphPort
                .neighborhood(chapterId, depth)
                // 그래프에 노드가 없는 published 뼈대는 정상 상태가 아니다. 빈 이웃으로 내리면
                // "관련 챕터 없음"과 구분되지 않아 파생 저장소 유실이 조용히 지나간다.
                .orElseThrow(() -> new EntityNotFoundException("챕터 그래프", chapterId));

        return new ChapterNeighborhoodView(
                ChapterNodeView.from(neighborhood.center()),
                neighborhood.depth(),
                nodes(neighborhood.neighbors()),
                neighborhood.relations().stream()
                        .map(edge -> new ChapterNeighborhoodView.Relation(
                                edge.fromChapterId(), edge.toChapterId(), edge.relationType()))
                        .toList());
    }

    private SkeletonEntryView entryPoints(Skeleton skeleton) {
        return new SkeletonEntryView(
                skeleton.id(), skeleton.topicId(), nodes(loadChapterGraphPort.entryPoints(skeleton.id())));
    }

    private static List<ChapterNodeView> nodes(List<LoadChapterGraphPort.GraphChapter> chapters) {
        return chapters.stream().map(ChapterNodeView::from).toList();
    }

    private static Skeleton requirePublished(java.util.Optional<Skeleton> found, String label, Object id) {
        Skeleton skeleton = found.orElseThrow(() -> new EntityNotFoundException(label, id));
        if (!skeleton.isPublished()) {
            // 존재 여부를 나누지 않는다. 사용자에게 "생성 중인 뼈대가 있다"는 사실 자체가 쓸 수 없는 정보다.
            throw new EntityNotFoundException(label, id);
        }
        return skeleton;
    }
}
