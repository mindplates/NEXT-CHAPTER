package com.mindplates.nextchapter.application.chapter.service;

import com.mindplates.nextchapter.application.chapter.port.in.ComposeChapterUseCase;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterVersionPort;
import com.mindplates.nextchapter.application.chapter.view.ComposedChapterView;
import com.mindplates.nextchapter.application.skeleton.service.PublishedSkeletonGuard;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersion;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.layer.model.ChapterLayer;
import com.mindplates.nextchapter.domain.layer.model.LayerComposition;
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
 * <p>레이어를 <b>적재하지 않고 빈 레이어를 쓴다.</b> 레이어 저장은 M4 P4.1 이고, 그때 이 자리에 적재
 * 포트가 들어온다. 경계를 지금 그어 두는 이유는, 나중에 그으려면 이미 "본문을 그대로 내리는" 코드가
 * 호출부마다 퍼져 있기 때문이다. 합성 규칙 자체는 {@link LayerComposition} 에 있고 이미 검증된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChapterCompositionService implements ComposeChapterUseCase {

    private final PublishedSkeletonGuard publishedSkeletonGuard;
    private final LoadChapterPort loadChapterPort;
    private final LoadChapterVersionPort loadChapterVersionPort;

    @Override
    public ComposedChapterView compose(Long userId, Long chapterId, DeliveryFormat format) {
        DeliveryFormat resolved = format == null ? DeliveryFormat.WEB : format;
        Chapter chapter =
                loadChapterPort.findById(chapterId).orElseThrow(() -> new EntityNotFoundException("챕터", chapterId));
        publishedSkeletonGuard.requireById(chapter.skeletonId());

        // published 뼈대인데 본문이 없는 것은 정상 상태가 아니다 — published 전환 조건이 "전 챕터 완료"다.
        // 빈 블록 목록으로 내리면 그 불일치가 "내용 없는 챕터"로 보인다.
        ChapterVersion latest = loadChapterVersionPort
                .findLatest(chapterId)
                .orElseThrow(() -> new EntityNotFoundException("챕터 본문", chapterId));

        ChapterLayer layer = ChapterLayer.empty(userId, chapterId);
        return new ComposedChapterView(
                chapter.id(),
                chapter.chapterKey(),
                chapter.title(),
                chapter.summary(),
                latest.version(),
                resolved,
                LayerComposition.compose(latest.body(), resolved, layer),
                !layer.isEmpty(),
                latest.isRevision(),
                latest.changeSummary(),
                latest.createdAt());
    }
}
