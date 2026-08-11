package com.mindplates.nextchapter.domain.chapter.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDateTime;

/**
 * 챕터 본문의 스냅샷 한 장. 델타가 아니라 전문이다.
 *
 * <p>스냅샷인 이유는 세 가지다 — 승인 이력 조회가 diff 재구성 없이 끝나야 하고, 수정 전후 오답률
 * 비교가 임의 시점의 본문을 필요로 하고, 본문이 수 KB 라 저장 비용이 무시할 만하다.
 *
 * @param changeSummary "이 챕터는 개선되었습니다" 배지가 노출할 변경 요약
 */
public record ChapterVersion(
        Long id,
        Long chapterId,
        int version,
        BlockDocument body,
        String changeSummary,
        ChapterVersionSource source,
        String createdBy,
        LocalDateTime createdAt) {

    public ChapterVersion {
        if (chapterId == null) {
            throw new IllegalArgumentException("버전은 챕터에 속해야 합니다.");
        }
        if (version < 1) {
            throw new IllegalArgumentException("버전은 1 이상이어야 합니다: " + version);
        }
        if (body == null) {
            throw new IllegalArgumentException("본문은 필수입니다.");
        }
        if (source == null) {
            throw new IllegalArgumentException("버전 출처는 필수입니다.");
        }
        changeSummary = Strings.trimToNull(changeSummary);
        createdBy = Strings.trimToNull(createdBy);
    }

    public boolean isRevision() {
        return source != ChapterVersionSource.GENERATED;
    }
}
