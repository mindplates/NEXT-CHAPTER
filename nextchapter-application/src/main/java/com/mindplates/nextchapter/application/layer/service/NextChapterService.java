package com.mindplates.nextchapter.application.layer.service;

import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterGraphPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterGraphPort.GraphChapter;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.layer.port.in.RecommendNextChapterUseCase;
import com.mindplates.nextchapter.application.layer.port.out.LoadChapterUnderstandingPort;
import com.mindplates.nextchapter.application.layer.view.NextChapterView;
import com.mindplates.nextchapter.application.skeleton.service.PublishedSkeletonGuard;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.layer.model.ChapterUnderstanding;
import com.mindplates.nextchapter.domain.layer.model.NextChapterChoice;
import com.mindplates.nextchapter.domain.layer.model.UnderstandingLevel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다음에 볼 챕터.
 *
 * <p>이웃은 <b>Neo4j</b>에서, 이해도는 <b>Postgres</b>에서 읽는다. 그래프 탐색은 가변 길이 질문이라 그래프
 * 저장소가 답해야 하고, 이해도는 방금 기록된 신호를 곧바로 읽어야 하므로 원천에서 읽는다.
 *
 * <p>규칙 자체는 {@link NextChapterChoice} 에 있다. 여기서 하는 일은 두 저장소의 값을 그 함수의 입력으로
 * 맞추고 제목을 붙이는 것뿐이다 — 규칙을 서비스에 두면 그것을 확인하려고 저장소 두 개를 세워야 한다.
 *
 * <p>1홉만 본다. 2홉 이상을 후보로 내밀면 "다음"이 아니라 목차가 되고, 사용자가 지금 갈 수 있는 곳이 아닌
 * 것까지 섞인다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NextChapterService implements RecommendNextChapterUseCase {

    /** 다음 한 걸음만 본다. */
    private static final int ONE_HOP = 1;

    private final PublishedSkeletonGuard publishedSkeletonGuard;
    private final LoadChapterPort loadChapterPort;
    private final LoadChapterGraphPort loadChapterGraphPort;
    private final LoadChapterUnderstandingPort loadChapterUnderstandingPort;

    @Override
    public NextChapterView next(Long userId, Long chapterId) {
        Chapter chapter =
                loadChapterPort.findById(chapterId).orElseThrow(() -> new EntityNotFoundException("챕터", chapterId));
        publishedSkeletonGuard.requireById(chapter.skeletonId());

        LoadChapterGraphPort.Neighborhood neighborhood = loadChapterGraphPort
                .neighborhood(chapterId, ONE_HOP)
                // published 뼈대인데 그래프에 노드가 없는 것은 파생 저장소 유실이다. 빈 추천으로
                // 내리면 "갈 곳이 없다"와 구분되지 않는다.
                .orElseThrow(() -> new EntityNotFoundException("챕터 그래프", chapterId));

        Map<Long, ChapterUnderstanding> understandings = new HashMap<>();
        loadChapterUnderstandingPort
                .summarizeBySkeleton(userId, chapter.skeletonId())
                .forEach(understanding -> understandings.put(understanding.chapterId(), understanding));

        UnderstandingLevel currentLevel = understandings
                .getOrDefault(chapterId, ChapterUnderstanding.none(chapterId))
                .level();

        Map<Long, GraphChapter> nodes = new HashMap<>();
        neighborhood.neighbors().forEach(node -> nodes.put(node.chapterId(), node));

        List<NextChapterChoice.Recommendation> ranked =
                NextChapterChoice.rank(currentLevel, neighbors(chapterId, neighborhood), understandings);

        List<NextChapterView.Candidate> candidates = new ArrayList<>();
        for (NextChapterChoice.Recommendation recommendation : ranked) {
            GraphChapter node = nodes.get(recommendation.chapterId());
            if (node == null) {
                continue;
            }
            ChapterUnderstanding understanding =
                    understandings.getOrDefault(node.chapterId(), ChapterUnderstanding.none(node.chapterId()));
            candidates.add(new NextChapterView.Candidate(
                    node.chapterId(),
                    node.chapterKey(),
                    node.title(),
                    recommendation.reason(),
                    understanding.level(),
                    understanding.completed()));
        }
        return new NextChapterView(chapterId, currentLevel, List.copyOf(candidates));
    }

    /**
     * 간선을 방향이 있는 이웃으로 바꾼다.
     *
     * <p>방향을 잃으면 되돌리기와 전진이 뒤섞인다 — 들어오는 선행은 "이 챕터의 선행"이고 나가는 선행은
     * "다음 단계"다. 두 챕터가 두 종류의 관계로 연결돼 있으면 이웃이 두 번 나오고, 우선순위 규칙이 그중
     * 먼저 맞는 것을 고른다.
     */
    private static List<NextChapterChoice.Neighbor> neighbors(
            Long chapterId, LoadChapterGraphPort.Neighborhood neighborhood) {
        return neighborhood.relations().stream()
                .map(edge -> edge.toChapterId().equals(chapterId)
                        ? new NextChapterChoice.Neighbor(edge.fromChapterId(), edge.relationType(), true)
                        : new NextChapterChoice.Neighbor(edge.toChapterId(), edge.relationType(), false))
                .filter(neighbor -> !neighbor.chapterId().equals(chapterId))
                .toList();
    }
}
