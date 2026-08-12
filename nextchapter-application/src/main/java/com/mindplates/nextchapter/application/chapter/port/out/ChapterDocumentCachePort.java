package com.mindplates.nextchapter.application.chapter.port.out;

import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 공용 블록 문서 캐시.
 *
 * <p><b>무효화 메서드가 없다.</b> 키에 버전과 형태가 들어가므로 뼈대가 수정되면 새 키를 보게 되고,
 * 구버전 키는 TTL 로 자연 만료된다. 명시적 무효화를 두지 않는 이유는 삭제 호출이 <b>한 번만 누락돼도</b>
 * 사용자가 구버전을 계속 보게 되고, 그게 에러 없이 조용히 틀리기 때문이다 — "개선되었습니다" 배지와
 * 실제 본문이 어긋나는 상태가 정확히 그 형태다.
 *
 * <p>캐시에 들어가는 것은 <b>개인화되지 않은</b> 문서다. 레이어를 합성한 결과를 넣으면 키에 사용자가
 * 들어가야 하고, 그러면 공유되는 것이 없어 캐시의 의미가 사라진다. 합성은 캐시 조회 <b>뒤에</b> 온다.
 */
public interface ChapterDocumentCachePort {

    Optional<CachedChapterDocument> find(Long chapterId, int version, DeliveryFormat format);

    /** 값이 자기 키를 들고 있다 — 키와 값이 어긋나는 실수를 타입이 막는다. */
    void put(CachedChapterDocument document);

    /**
     * @param blocks 이미 형태별로 걸러진 블록. 형태가 키에 있으므로 저장 시점에 걸러 둔다
     * @param improvedFromPrevious "이 챕터는 개선되었습니다" 배지의 근거. 버전에 딸린 사실이라 함께 캐시한다
     */
    record CachedChapterDocument(
            Long chapterId,
            int version,
            DeliveryFormat format,
            List<Block> blocks,
            boolean improvedFromPrevious,
            String changeSummary,
            LocalDateTime versionCreatedAt) {}
}
