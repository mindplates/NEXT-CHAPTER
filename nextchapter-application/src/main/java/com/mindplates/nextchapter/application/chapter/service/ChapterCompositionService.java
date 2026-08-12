package com.mindplates.nextchapter.application.chapter.service;

import com.mindplates.nextchapter.application.chapter.port.in.ComposeChapterUseCase;
import com.mindplates.nextchapter.application.chapter.port.out.ChapterDocumentCachePort;
import com.mindplates.nextchapter.application.chapter.port.out.ChapterDocumentCachePort.CachedChapterDocument;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterVersionPort;
import com.mindplates.nextchapter.application.chapter.view.ComposedChapterView;
import com.mindplates.nextchapter.application.skeleton.service.PublishedSkeletonGuard;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockDocument;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersion;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.layer.model.ChapterLayer;
import com.mindplates.nextchapter.domain.layer.model.LayerComposition;
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
 *   <li><b>공용 문서</b> — 캐시 대상. 키에 버전과 형태가 들어가므로 무효화 코드가 없다
 *   <li><b>합성 결과</b> — 캐시 대상이 아니다. 사용자마다 다르므로 키에 사용자가 들어가야 하고, 그러면
 *       공유되는 것이 없어 캐시의 의미가 사라진다
 * </ol>
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
    private final ChapterDocumentCachePort chapterDocumentCachePort;

    @Override
    public ComposedChapterView compose(Long userId, Long chapterId, DeliveryFormat format) {
        DeliveryFormat resolved = format == null ? DeliveryFormat.WEB : format;
        Chapter chapter =
                loadChapterPort.findById(chapterId).orElseThrow(() -> new EntityNotFoundException("챕터", chapterId));
        publishedSkeletonGuard.requireById(chapter.skeletonId());

        CachedChapterDocument document = sharedDocument(chapter, resolved);
        ChapterLayer layer = ChapterLayer.empty(userId, chapterId);

        return new ComposedChapterView(
                chapter.id(),
                chapter.chapterKey(),
                chapter.title(),
                chapter.summary(),
                document.version(),
                resolved,
                compose(document, resolved, layer),
                !layer.isEmpty(),
                document.improvedFromPrevious(),
                document.changeSummary(),
                document.versionCreatedAt());
    }

    /**
     * 공용 문서. 캐시에 있으면 그것을 쓰고, 없으면 원천에서 읽어 채운다.
     *
     * <p>버전을 <b>챕터 행에서</b> 가져와 키를 만든다. 그래서 캐시 히트일 때 본문 스냅샷(JSONB)을 읽지
     * 않는다 — 그것이 이 조회에서 가장 비싼 부분이다. 챕터 행과 버전 스냅샷은 같은 트랜잭션에서 쓰이므로
     * 두 값이 어긋나지 않는다.
     */
    private CachedChapterDocument sharedDocument(Chapter chapter, DeliveryFormat format) {
        if (!chapter.hasBody()) {
            // published 전환 조건이 "전 챕터 완료"이므로 본문 없는 published 챕터는 정상 상태가 아니다.
            // 빈 블록 목록으로 내리면 그 불일치가 "내용 없는 챕터"로 보인다.
            throw new EntityNotFoundException("챕터 본문", chapter.id());
        }
        int version = chapter.currentVersion();
        return chapterDocumentCachePort
                .find(chapter.id(), version, format)
                .orElseGet(() -> cache(chapter, version, format));
    }

    private CachedChapterDocument cache(Chapter chapter, int version, DeliveryFormat format) {
        ChapterVersion snapshot = loadChapterVersionPort
                .find(chapter.id(), version)
                .orElseThrow(() -> new EntityNotFoundException("챕터 본문", chapter.id()));
        CachedChapterDocument document = new CachedChapterDocument(
                chapter.id(),
                version,
                format,
                snapshot.body().blocksFor(format),
                snapshot.isRevision(),
                snapshot.changeSummary(),
                snapshot.createdAt());
        chapterDocumentCachePort.put(document);
        return document;
    }

    /**
     * 캐시된 블록은 이미 형태별로 걸러져 있다. 레이어가 비어 있으면 그대로 쓴다 — 합성 함수를 부르려고
     * {@link BlockDocument} 를 다시 만들 이유가 없다.
     */
    private static List<Block> compose(CachedChapterDocument document, DeliveryFormat format, ChapterLayer layer) {
        if (layer.isEmpty()) {
            return document.blocks();
        }
        return LayerComposition.compose(new BlockDocument(document.blocks()), format, layer);
    }
}
