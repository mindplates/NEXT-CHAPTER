package com.mindplates.nextchapter.application.chapter.service;

import com.mindplates.nextchapter.application.chapter.port.in.ComposeChapterUseCase;
import com.mindplates.nextchapter.application.chapter.port.out.ChapterDocumentCachePort.CachedChapterDocument;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.view.BlockView;
import com.mindplates.nextchapter.application.chapter.view.ComposedChapterView;
import com.mindplates.nextchapter.application.layer.port.out.LoadChapterUnderstandingPort;
import com.mindplates.nextchapter.application.layer.port.out.LoadSupplementBlockPort;
import com.mindplates.nextchapter.application.skeleton.service.PublishedSkeletonGuard;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockDocument;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.layer.model.ChapterLayer;
import com.mindplates.nextchapter.domain.layer.model.LayerComposition;
import com.mindplates.nextchapter.domain.layer.model.UnderstandingLevel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자의 챕터 조회 — 합성 지점.
 *
 * <p>여기서 하는 "합성"은 <b>레이어 병합</b>이지 HTML 생성이 아니다. 내리는 것은 블록 목록이고, 렌더링은
 * 클라이언트가 한다 — 그래서 모바일·태블릿 앱이 붙어도 이 API 가 그대로다.
 *
 * <p><b>사용자는 항상 최신 버전을 본다.</b> 임의 버전 조회는 운영자 경로에만 있다(수정 전후 비교용).
 * 사용자에게 구버전을 고정해 주면 "이 챕터는 개선되었습니다" 배지와 실제 본문이 어긋난다.
 *
 * <p>경계가 둘로 나뉘어 있다.
 *
 * <ol>
 *   <li><b>공용 문서</b> — {@link SharedChapterDocuments} 가 읽고 캐시한다. 키에 버전과 형태가 들어가므로
 *       무효화 코드가 없다
 *   <li><b>합성 결과</b> — 캐시 대상이 아니다. 사용자마다 다르므로 키에 사용자가 들어가야 하고, 그러면
 *       공유되는 것이 없어 캐시의 의미가 사라진다
 * </ol>
 *
 * <p>개인화가 바꾸는 것은 둘뿐이다 — <b>보충 블록 삽입</b>(P4.3)과 <b>퀴즈 문항 선택</b>(P4.4). 설명 본문은
 * 누구에게나 같은 텍스트여야 하고, 그래야 "같은 지점에서 틀렸다"를 겹쳐 볼 수 있다. 합성 규칙 자체는
 * {@link LayerComposition} 에 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChapterCompositionService implements ComposeChapterUseCase {

    private final PublishedSkeletonGuard publishedSkeletonGuard;
    private final LoadChapterPort loadChapterPort;
    private final SharedChapterDocuments sharedChapterDocuments;
    private final LoadSupplementBlockPort loadSupplementBlockPort;
    private final LoadChapterUnderstandingPort loadChapterUnderstandingPort;

    @Override
    public ComposedChapterView compose(Long userId, Long chapterId, DeliveryFormat format) {
        DeliveryFormat resolved = format == null ? DeliveryFormat.WEB : format;
        Chapter chapter =
                loadChapterPort.findById(chapterId).orElseThrow(() -> new EntityNotFoundException("챕터", chapterId));
        publishedSkeletonGuard.requireById(chapter.skeletonId());

        CachedChapterDocument document = sharedChapterDocuments.current(chapter, resolved);
        // 레이어 적재 지점. 캐시 조회 **뒤에** 온다 — 개인화 결과는 공용 키에 들어갈 수 없다.
        ChapterLayer layer =
                new ChapterLayer(userId, chapterId, loadSupplementBlockPort.findByUserAndChapter(userId, chapterId));
        // 퀴즈 문항 선택에만 쓴다. 설명 본문은 이 값과 무관하게 누구에게나 같다.
        UnderstandingLevel level =
                loadChapterUnderstandingPort.findByChapter(userId, chapterId).level();

        return new ComposedChapterView(
                chapter.id(),
                chapter.chapterKey(),
                chapter.title(),
                chapter.summary(),
                document.version(),
                resolved,
                // 퀴즈 정답은 여기서 빠진다 — 채점은 서버가 하고, 클라이언트가 계산하면 위조할 수 있다.
                BlockView.forLearner(compose(document, resolved, layer, level)),
                !layer.isEmpty(),
                document.improvedFromPrevious(),
                document.changeSummary(),
                document.versionCreatedAt());
    }

    /**
     * 캐시된 블록은 이미 형태별로 걸러져 있지만 <b>난이도로는 걸러져 있지 않다</b> — 그 선택이 사용자별이라
     * 공용 캐시에 들어갈 수 없다. 그래서 레이어가 비어 있어도 합성 함수를 지난다.
     */
    private static List<Block> compose(
            CachedChapterDocument document, DeliveryFormat format, ChapterLayer layer, UnderstandingLevel level) {
        return LayerComposition.compose(new BlockDocument(document.blocks()), format, layer, level);
    }
}
