package com.mindplates.nextchapter.application.layer.service;

import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.layer.port.in.GetChapterLayerUseCase;
import com.mindplates.nextchapter.application.layer.port.out.LoadChapterUnderstandingPort;
import com.mindplates.nextchapter.application.layer.view.ChapterUnderstandingView;
import com.mindplates.nextchapter.application.layer.view.SkeletonLayerView;
import com.mindplates.nextchapter.application.skeleton.service.PublishedSkeletonGuard;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.layer.model.ChapterUnderstanding;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 레이어 조회.
 *
 * <p><b>레이어는 신호에서 파생한다</b> — 별도 집계 테이블이 없다. 이중 기록을 만들면 둘이 어긋날 수 있고,
 * 그 어긋남은 에러 없이 잘못된 추천으로 나타난다.
 *
 * <p>레이어가 <b>챕터 ID 기준</b>이라는 점이 여기서 드러난다. 신호는 챕터에 붙고 버전은 데이터로만 담기므로,
 * 뼈대가 수정돼 버전이 올라가도 이 조회 결과는 그대로다 — 개인 학습 기록이 살아남는다.
 *
 * <p>보지 않은 챕터도 목록에 넣는다. 뼈대는 항상 완결 상태이므로 "전체 중 어디까지"의 분모가 있어야 하고,
 * 본 것만 내리면 그 분모가 사라진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChapterLayerService implements GetChapterLayerUseCase {

    private final PublishedSkeletonGuard publishedSkeletonGuard;
    private final LoadChapterPort loadChapterPort;
    private final LoadChapterUnderstandingPort loadChapterUnderstandingPort;

    @Override
    public SkeletonLayerView bySkeletonId(Long userId, Long skeletonId) {
        publishedSkeletonGuard.requireById(skeletonId);

        List<Chapter> chapters = loadChapterPort.findBySkeletonId(skeletonId);
        Map<Long, ChapterUnderstanding> byChapter = new HashMap<>();
        loadChapterUnderstandingPort
                .summarizeBySkeleton(userId, skeletonId)
                .forEach(understanding -> byChapter.put(understanding.chapterId(), understanding));

        List<ChapterUnderstandingView> views = chapters.stream()
                .map(chapter -> ChapterUnderstandingView.of(
                        chapter, byChapter.getOrDefault(chapter.id(), ChapterUnderstanding.none(chapter.id()))))
                .toList();

        return new SkeletonLayerView(
                skeletonId,
                views.size(),
                (int) views.stream().filter(view -> view.lastSeenAt() != null).count(),
                (int) views.stream().filter(ChapterUnderstandingView::completed).count(),
                views);
    }

    @Override
    public ChapterUnderstandingView byChapterId(Long userId, Long chapterId) {
        Chapter chapter =
                loadChapterPort.findById(chapterId).orElseThrow(() -> new EntityNotFoundException("챕터", chapterId));
        publishedSkeletonGuard.requireById(chapter.skeletonId());
        return ChapterUnderstandingView.of(chapter, loadChapterUnderstandingPort.findByChapter(userId, chapterId));
    }
}
