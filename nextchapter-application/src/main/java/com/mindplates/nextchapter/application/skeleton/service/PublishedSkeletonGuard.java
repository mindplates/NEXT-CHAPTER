package com.mindplates.nextchapter.application.skeleton.service;

import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * "사용자에게 보여도 되는 뼈대인가"의 단일 판정 지점.
 *
 * <p>사용자 경로가 늘어날 때마다(탐색, 챕터 조회, 신호 기록, 추천) 같은 검사가 필요하다. 호출부마다
 * 복제하면 새 경로를 추가할 때 <b>잊는 쪽이 기본값</b>이 되고, 그 실패는 에러가 아니라 미완성 콘텐츠가
 * 사용자에게 나가는 것이다 — 예산 검사를 AI 게이트웨이 한 곳에만 둔 것과 같은 이유다.
 *
 * <p>공개 여부를 <b>Postgres</b>에 묻는다. Neo4j 는 파생 저장소라 반영이 뒤처질 수 있고, 그 지연이 접근
 * 판정에 섞이면 "잠깐 열렸다 닫히는" 상태가 만들어진다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublishedSkeletonGuard {

    private final LoadSkeletonPort loadSkeletonPort;

    public Skeleton requireById(Long skeletonId) {
        return require(loadSkeletonPort.findById(skeletonId), "공개된 뼈대", skeletonId);
    }

    public Skeleton requireByTopicId(Long topicId) {
        return require(loadSkeletonPort.findByTopicId(topicId), "공개된 뼈대(주제)", topicId);
    }

    /**
     * 없는 것과 공개되지 않은 것을 <b>같은 404 로</b> 접는다. 나누면 "생성 중인 뼈대가 있다"는 사실이
     * 새는데, 그건 사용자가 쓸 수 없는 정보이고 카탈로그의 진행 상황을 열거할 수 있게 만든다.
     */
    private static Skeleton require(Optional<Skeleton> found, String label, Object id) {
        Skeleton skeleton = found.orElseThrow(() -> new EntityNotFoundException(label, id));
        if (!skeleton.isPublished()) {
            throw new EntityNotFoundException(label, id);
        }
        return skeleton;
    }
}
