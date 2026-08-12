package com.mindplates.nextchapter.application.chapter.service;

import com.mindplates.nextchapter.application.chapter.port.out.ChapterDocumentCachePort;
import com.mindplates.nextchapter.application.chapter.port.out.ChapterDocumentCachePort.CachedChapterDocument;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterVersionPort;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersion;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공용 블록 문서를 읽는 단일 지점 — <b>캐시 정책이 여기에만 있다.</b>
 *
 * <p>챕터 조회와 신호 기록이 같은 문서를 필요로 한다(신호는 blockId 가 그 버전에 실재하는지 확인해야
 * 한다). 각자 캐시를 다루면 한쪽이 캐시를 채우지 않거나 다른 키를 쓰게 되고, 그 어긋남은 에러 없이
 * "캐시가 잘 안 맞는다"로만 나타난다.
 *
 * <p>여기서 나오는 것은 <b>개인화되지 않은</b> 문서다. 레이어 합성은 이 뒤에 온다 — 그 경계가 캐시를
 * 가능하게 하는 조건이다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SharedChapterDocuments {

    private final LoadChapterVersionPort loadChapterVersionPort;
    private final ChapterDocumentCachePort chapterDocumentCachePort;

    /**
     * 현재 버전의 공용 문서. 캐시에 있으면 그것을 쓰고, 없으면 원천에서 읽어 채운다.
     *
     * <p>버전을 <b>챕터 행에서</b> 가져와 키를 만든다. 그래서 캐시 히트일 때 본문 스냅샷(JSONB)을 읽지
     * 않는다 — 그것이 이 조회에서 가장 비싼 부분이다. 챕터 행과 버전 스냅샷은 같은 트랜잭션에서 쓰이므로
     * 두 값이 어긋나지 않는다.
     */
    public CachedChapterDocument current(Chapter chapter, DeliveryFormat format) {
        if (!chapter.hasBody()) {
            // published 전환 조건이 "전 챕터 완료"이므로 본문 없는 published 챕터는 정상 상태가 아니다.
            // 빈 블록 목록으로 내리면 그 불일치가 "내용 없는 챕터"로 보인다.
            throw new EntityNotFoundException("챕터 본문", chapter.id());
        }
        return at(chapter.id(), chapter.currentVersion(), format);
    }

    /**
     * 특정 버전의 공용 문서. 신호가 <b>사용자가 실제로 소비한 버전</b>을 기준으로 검증돼야 하므로
     * 최신 버전만으로는 부족하다 — 읽는 사이에 수정이 반영될 수 있다.
     */
    public CachedChapterDocument at(Long chapterId, int version, DeliveryFormat format) {
        return chapterDocumentCachePort
                .find(chapterId, version, format)
                .orElseGet(() -> cache(chapterId, version, format));
    }

    private CachedChapterDocument cache(Long chapterId, int version, DeliveryFormat format) {
        ChapterVersion snapshot = loadChapterVersionPort
                .find(chapterId, version)
                .orElseThrow(() -> new EntityNotFoundException("챕터 버전", chapterId + ":v" + version));
        CachedChapterDocument document = new CachedChapterDocument(
                chapterId,
                version,
                format,
                snapshot.body().blocksFor(format),
                snapshot.isRevision(),
                snapshot.changeSummary(),
                snapshot.createdAt());
        chapterDocumentCachePort.put(document);
        return document;
    }
}
