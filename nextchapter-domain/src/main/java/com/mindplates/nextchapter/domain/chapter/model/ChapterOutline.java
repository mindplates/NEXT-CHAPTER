package com.mindplates.nextchapter.domain.chapter.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 챕터 하나의 개요 — 본문이 다뤄야 할 항목 목록.
 *
 * <p>비어 있는 개요를 허용하지 않는 이유는 그것이 어림이 아니라 사실이기 때문이다. 항목이 없으면 본문을
 * 쓸 재료가 없고, 그대로 두면 빈 본문이 생성되어 생성비만 쓴다.
 */
public record ChapterOutline(Long chapterId, List<String> points, LocalDateTime updatedAt) {

    /** 이보다 적으면 챕터를 나눈 의미가 없고, 많으면 한 챕터가 다른 챕터의 범위를 먹는다. */
    public static final int MIN_POINTS = 2;

    public static final int MAX_POINTS = 20;

    public ChapterOutline {
        if (chapterId == null) {
            throw new IllegalArgumentException("개요는 챕터에 속해야 합니다.");
        }
        if (points == null || points.size() < MIN_POINTS) {
            throw new IllegalArgumentException("개요 항목이 " + MIN_POINTS + "개 이상이어야 합니다. chapterId=" + chapterId);
        }
        if (points.size() > MAX_POINTS) {
            throw new IllegalArgumentException("개요 항목이 " + MAX_POINTS + "개를 넘습니다. chapterId=" + chapterId);
        }
        points = points.stream()
                .map(point -> Strings.requireText(point, "개요 항목"))
                .toList();
    }

    public static ChapterOutline of(Long chapterId, List<String> points) {
        return new ChapterOutline(chapterId, points, null);
    }
}
