package com.mindplates.nextchapter.application.chapter.port.out;

import com.mindplates.nextchapter.domain.chapter.model.ChapterRelation;
import java.util.List;

public interface SaveChapterRelationPort {

    List<ChapterRelation> saveAll(List<ChapterRelation> relations);

    /**
     * 뼈대의 관계를 전부 지운다. 그래프 재생성이 <b>덮어쓰기</b>여야 하기 때문이다 — 남겨 두면
     * 옛 그래프의 간선이 새 그래프에 섞여 순환이 생길 수 있고, 그 순환은 생성 시점 검증을 이미
     * 지나온 뒤라 걸리지 않는다.
     */
    void deleteBySkeletonId(Long skeletonId);
}
