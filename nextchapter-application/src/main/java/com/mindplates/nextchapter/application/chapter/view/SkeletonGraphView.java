package com.mindplates.nextchapter.application.chapter.view;

import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import java.util.List;

/**
 * 뼈대의 챕터 그래프. 관계를 <b>키로</b> 표현한다 — 사람이 읽는 검수 화면과 매핑 로그가 ID 보다 키를
 * 필요로 하고, 클라이언트도 키로 노드를 찾는 편이 단순하다.
 *
 * @param entryPointKeys 선행 관계가 없는 챕터. 개인 루프가 "처음 볼 챕터" 후보로 쓴다. 비어 있으면
 *     선행 관계에 순환이 있다는 뜻이고, 그건 생성 시점에 걸러진다
 */
public record SkeletonGraphView(
        Long skeletonId, List<Node> chapters, List<Edge> relations, List<String> entryPointKeys) {

    public record Node(Long chapterId, String chapterKey, String title, String summary, boolean hasBody, int version) {}

    public record Edge(String fromKey, String toKey, ChapterRelationType relationType) {}
}
