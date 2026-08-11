package com.mindplates.nextchapter.application.chapter.service;

import com.mindplates.nextchapter.application.chapter.port.in.GetChapterOutlinesUseCase;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterOutlinePort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadOutlineFindingPort;
import com.mindplates.nextchapter.application.chapter.view.ChapterOutlineView;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterOutline;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개요와 소견을 함께 내린다. 판단에 둘이 다 필요하다 — "이 두 챕터가 겹친다"만으로는 무엇을 지울지
 * 정할 수 없고, 개요 항목을 나란히 봐야 결정할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChapterOutlineQueryService implements GetChapterOutlinesUseCase {

    private final LoadChapterPort loadChapterPort;
    private final LoadChapterOutlinePort loadChapterOutlinePort;
    private final LoadOutlineFindingPort loadOutlineFindingPort;

    @Override
    public ChapterOutlineView bySkeletonId(Long skeletonId) {
        List<Chapter> chapters = loadChapterPort.findBySkeletonId(skeletonId);
        Map<Long, ChapterOutline> outlinesByChapter = loadChapterOutlinePort.findBySkeletonId(skeletonId).stream()
                .collect(Collectors.toMap(ChapterOutline::chapterId, outline -> outline));

        List<ChapterOutlineView.Entry> entries = chapters.stream()
                .map(chapter -> new ChapterOutlineView.Entry(
                        chapter.id(),
                        chapter.chapterKey(),
                        chapter.title(),
                        outlinesByChapter.containsKey(chapter.id())
                                ? outlinesByChapter.get(chapter.id()).points()
                                : List.of()))
                .toList();

        List<ChapterOutlineView.Finding> findings = loadOutlineFindingPort.findBySkeletonId(skeletonId).stream()
                .map(finding ->
                        new ChapterOutlineView.Finding(finding.findingType(), finding.chapterKeys(), finding.detail()))
                .toList();

        return new ChapterOutlineView(skeletonId, entries, findings);
    }
}
