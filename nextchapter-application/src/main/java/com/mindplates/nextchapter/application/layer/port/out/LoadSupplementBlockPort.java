package com.mindplates.nextchapter.application.layer.port.out;

import com.mindplates.nextchapter.domain.layer.model.SupplementBlock;
import java.util.List;
import java.util.Optional;

public interface LoadSupplementBlockPort {

    /** 합성이 쓰는 조회. 챕터 조회마다 지나므로 (사용자, 챕터) 인덱스가 있다. */
    List<SupplementBlock> findByUserAndChapter(Long userId, Long chapterId);

    /** 같은 지점을 다시 요청했는지 확인한다 — 있으면 다시 만들지 않는다(비용 방어선). */
    Optional<SupplementBlock> findByAnchor(Long userId, Long chapterId, String anchorBlockId);
}
