package com.mindplates.nextchapter.application.chapter.service;

import com.mindplates.nextchapter.application.chapter.port.in.GetSkeletonGraphUseCase;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterRelationPort;
import com.mindplates.nextchapter.application.chapter.view.SkeletonGraphView;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그래프 조회. 챕터와 관계를 각각 한 번씩만 읽고 메모리에서 키로 잇는다.
 *
 * <p>ID→키 변환을 여기서 하는 이유는 관계 테이블이 ID 로 저장되기 때문이다(참조 무결성은 ID 로만
 * 걸린다). 밖으로는 키를 내보내 검수 화면과 클라이언트가 사람이 읽는 식별자를 쓰게 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkeletonGraphQueryService implements GetSkeletonGraphUseCase {

    private final LoadChapterPort loadChapterPort;
    private final LoadChapterRelationPort loadChapterRelationPort;

    @Override
    public SkeletonGraphView bySkeletonId(Long skeletonId) {
        List<Chapter> chapters = loadChapterPort.findBySkeletonId(skeletonId);
        Map<Long, String> keysById = new HashMap<>();
        chapters.forEach(chapter -> keysById.put(chapter.id(), chapter.chapterKey()));

        List<ChapterRelation> relations = loadChapterRelationPort.findBySkeletonId(skeletonId);
        List<SkeletonGraphView.Edge> edges = relations.stream()
                .filter(relation ->
                        keysById.containsKey(relation.fromChapterId()) && keysById.containsKey(relation.toChapterId()))
                .map(relation -> new SkeletonGraphView.Edge(
                        keysById.get(relation.fromChapterId()),
                        keysById.get(relation.toChapterId()),
                        relation.relationType()))
                .toList();

        Set<String> hasPrerequisite = new HashSet<>();
        relations.stream()
                .filter(ChapterRelation::isPrerequisite)
                .map(relation -> keysById.get(relation.toChapterId()))
                .filter(java.util.Objects::nonNull)
                .forEach(hasPrerequisite::add);

        return new SkeletonGraphView(
                skeletonId,
                chapters.stream()
                        .map(chapter -> new SkeletonGraphView.Node(
                                chapter.id(),
                                chapter.chapterKey(),
                                chapter.title(),
                                chapter.summary(),
                                chapter.hasBody(),
                                chapter.currentVersion()))
                        .toList(),
                edges,
                chapters.stream()
                        .map(Chapter::chapterKey)
                        .filter(key -> !hasPrerequisite.contains(key))
                        .toList());
    }
}
