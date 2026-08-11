package com.mindplates.nextchapter.application.chapter.port.out;

import com.mindplates.nextchapter.domain.chapter.model.ChapterVersion;
import java.util.List;
import java.util.Optional;

public interface LoadChapterVersionPort {

    Optional<ChapterVersion> find(Long chapterId, int version);

    Optional<ChapterVersion> findLatest(Long chapterId);

    /** 이력 목록. 본문 없이 메타만 필요할 때가 많지만, 스냅샷이 수 KB 라 나누지 않는다. */
    List<ChapterVersion> findAllByChapterId(Long chapterId);
}
