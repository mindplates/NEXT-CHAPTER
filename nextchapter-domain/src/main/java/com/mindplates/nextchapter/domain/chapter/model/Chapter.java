package com.mindplates.nextchapter.domain.chapter.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDateTime;

/**
 * 챕터. 본문은 {@link ChapterVersion} 에 스냅샷으로 있고, 이 타입은 <b>정체성과 카운터</b>를 든다.
 *
 * <p>{@code blockIdSequence} 를 챕터에 두는 것이 블록 ID 재사용을 막는 실제 장치다. 문서에서 최대
 * 순번을 계산해 다음 값을 정하면, 블록을 지우고 다시 만들 때 지워진 ID 가 되살아난다 — 그러면 v1 의
 * {@code b3} 신호와 v5 의 {@code b3} 신호가 한 지점에 섞인다.
 *
 * @param currentVersion 0 이면 본문이 아직 없다 (생성 파이프라인이 ③ 에 도달하기 전)
 * @param chapterKey 뼈대 안에서 유일한 사람이 읽는 키. 그래프 생성이 ID 없이 관계를 표현할 때 쓴다
 */
public record Chapter(
        Long id,
        Long skeletonId,
        String chapterKey,
        String title,
        String summary,
        int currentVersion,
        int blockIdSequence,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public Chapter {
        if (skeletonId == null) {
            throw new IllegalArgumentException("챕터는 뼈대에 속해야 합니다.");
        }
        chapterKey = Strings.requireMaxLength(Strings.requireText(chapterKey, "챕터 키"), 120, "챕터 키");
        title = Strings.requireMaxLength(Strings.requireText(title, "챕터 제목"), 300, "챕터 제목");
        summary = Strings.trimToNull(summary);
        if (currentVersion < 0) {
            throw new IllegalArgumentException("현재 버전은 0 이상이어야 합니다.");
        }
        if (blockIdSequence < 0) {
            throw new IllegalArgumentException("블록 ID 카운터는 0 이상이어야 합니다.");
        }
    }

    public static Chapter create(Long skeletonId, String chapterKey, String title, String summary, int sortOrder) {
        return new Chapter(null, skeletonId, chapterKey, title, summary, 0, 0, sortOrder, null, null);
    }

    public boolean hasBody() {
        return currentVersion > 0;
    }

    /** 다음에 발급할 블록 ID 순번. 카운터가 0 이면 1 부터 시작한다. */
    public int nextBlockSequence() {
        return blockIdSequence + 1;
    }

    /**
     * 새 버전이 확정된 뒤의 챕터 상태.
     *
     * <p>카운터를 <b>되돌리지 않는다</b> — 승계가 소비한 값보다 작아질 수 없다. 작아지면 다음 발급이
     * 이미 쓰인 ID 를 다시 내준다.
     */
    public Chapter withNewVersion(int version, int consumedSequence) {
        if (version <= currentVersion) {
            throw new IllegalArgumentException("버전은 증가해야 합니다. 현재=" + currentVersion + " 요청=" + version);
        }
        return new Chapter(
                id,
                skeletonId,
                chapterKey,
                title,
                summary,
                version,
                Math.max(blockIdSequence, consumedSequence),
                sortOrder,
                createdAt,
                updatedAt);
    }

    /** 제목·요약만 고친다. 개요 단계가 그래프 단계의 결과를 다듬을 때 쓴다. */
    public Chapter withOutline(String newTitle, String newSummary) {
        return new Chapter(
                id,
                skeletonId,
                chapterKey,
                newTitle,
                newSummary,
                currentVersion,
                blockIdSequence,
                sortOrder,
                createdAt,
                updatedAt);
    }
}
