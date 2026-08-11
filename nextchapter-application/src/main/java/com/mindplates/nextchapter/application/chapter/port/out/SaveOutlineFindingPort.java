package com.mindplates.nextchapter.application.chapter.port.out;

import com.mindplates.nextchapter.domain.chapter.model.OutlineFinding;
import java.util.List;

public interface SaveOutlineFindingPort {

    List<OutlineFinding> saveAll(List<OutlineFinding> findings);

    /** 재생성 시 옛 소견을 지운다 — 이미 고친 겹침이 검수 화면에 남아 있으면 판단을 흐린다. */
    void deleteBySkeletonId(Long skeletonId);
}
