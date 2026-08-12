package com.mindplates.nextchapter.domain.layer.model;

import java.util.List;

/**
 * 한 사용자의 한 챕터에 대한 레이어. 화면에 보이는 것은 {@code 뼈대 + 이 레이어}의 합성 결과다.
 *
 * <p><b>챕터 ID 기준</b>이다. 뼈대가 수정돼 버전이 올라가도 레이어는 그대로 붙어 있어, 개인 학습 기록이
 * 살아남는다 — 버전을 키에 넣으면 개선이 일어날 때마다 그 사람의 이력이 끊긴다.
 *
 * <p>비어 있는 레이어가 정상 상태다. 처음 들어온 사용자에게는 아무 기록이 없고, 그때 합성은 항등이다.
 */
public record ChapterLayer(Long userId, Long chapterId, List<SupplementBlock> supplements) {

    public ChapterLayer {
        supplements = supplements == null ? List.of() : List.copyOf(supplements);
    }

    /** 아직 아무 기록이 없는 사용자. 합성이 항등이 된다. */
    public static ChapterLayer empty(Long userId, Long chapterId) {
        return new ChapterLayer(userId, chapterId, List.of());
    }

    public boolean isEmpty() {
        return supplements.isEmpty();
    }
}
