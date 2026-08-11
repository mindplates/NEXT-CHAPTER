package com.mindplates.nextchapter.application.chapter.view;

import com.mindplates.nextchapter.domain.chapter.model.OutlineFindingType;
import java.util.List;

/**
 * 뼈대의 개요 전체 + 소견. 검수 화면이 한 번에 본다.
 *
 * <p>소견을 개요와 함께 내리는 이유는 판단에 둘이 다 필요하기 때문이다 — "이 두 챕터가 겹친다"만으로는
 * 무엇을 지울지 정할 수 없고, 개요 항목을 나란히 봐야 결정할 수 있다.
 */
public record ChapterOutlineView(Long skeletonId, List<Entry> outlines, List<Finding> findings) {

    public record Entry(Long chapterId, String chapterKey, String title, List<String> points) {}

    public record Finding(OutlineFindingType findingType, List<String> chapterKeys, String detail) {}
}
