package com.mindplates.nextchapter.application.chapter.view;

import java.util.List;

/**
 * 뼈대의 진입 지점. 사용자가 주제를 고른 직후 보는 것이다.
 *
 * <p>진입점이 비어 있으면 선행 관계에 순환이 있다는 뜻이다 — 그 상태에서는 어느 챕터도 선행 조건을
 * 만족시킬 수 없다. 생성 시점에 걸러지므로 정상 경로에서는 비지 않는다.
 */
public record SkeletonEntryView(Long skeletonId, Long topicId, List<ChapterNodeView> entryPoints) {}
