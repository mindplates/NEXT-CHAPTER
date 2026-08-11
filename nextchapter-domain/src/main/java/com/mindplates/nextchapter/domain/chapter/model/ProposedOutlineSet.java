package com.mindplates.nextchapter.domain.chapter.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ② 개요 생성의 산출물 — 챕터별 개요 + 중복·누락 소견.
 *
 * <p>개요와 소견을 <b>한 응답</b>으로 받는다. 모델이 전 챕터를 한 번에 보므로 겹침을 만들지 않으면서
 * 동시에 남은 겹침을 보고할 수 있고, 호출이 두 번에서 한 번으로 줄어든다. 개요를 먼저 만든 뒤 별도
 * 호출로 검사하면 같은 정보를 두 번 보내게 된다.
 *
 * <p>검증의 축은 <b>모든 챕터가 정확히 한 번 덮이는지</b>다. 빠진 챕터는 본문을 쓸 재료가 없다는 뜻이고,
 * 중복된 챕터는 어느 개요가 진짜인지 알 수 없다는 뜻이다. 둘 다 다음 단계에서 조용히 빈 본문이나 임의
 * 선택으로 이어진다.
 */
public record ProposedOutlineSet(List<ProposedOutline> outlines, List<ProposedFinding> findings) {

    public ProposedOutlineSet {
        if (outlines == null || outlines.isEmpty()) {
            throw new IllegalArgumentException("개요가 비어 있습니다.");
        }
        outlines = List.copyOf(outlines);
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    /**
     * 챕터 키 집합과 정확히 일치하는지 검사한다.
     *
     * @throws IllegalArgumentException 빠진 챕터·모르는 챕터·중복 챕터가 있으면
     */
    public void requireCoverageOf(Set<String> chapterKeys) {
        Set<String> seen = new HashSet<>();
        for (ProposedOutline outline : outlines) {
            if (!chapterKeys.contains(outline.chapterKey())) {
                throw new IllegalArgumentException("개요가 없는 챕터를 가리킵니다: " + outline.chapterKey());
            }
            if (!seen.add(outline.chapterKey())) {
                throw new IllegalArgumentException("한 챕터에 개요가 두 개입니다: " + outline.chapterKey());
            }
        }
        Set<String> missing = new HashSet<>(chapterKeys);
        missing.removeAll(seen);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("개요가 없는 챕터가 있습니다: " + missing);
        }
    }

    /** 챕터 하나의 개요 제안. 아직 ID 가 없어 키로 챕터를 가리킨다. */
    public record ProposedOutline(String chapterKey, List<String> points) {

        public ProposedOutline {
            chapterKey = Strings.requireText(chapterKey, "챕터 키");
            if (points == null || points.isEmpty()) {
                throw new IllegalArgumentException("개요 항목이 없습니다: " + chapterKey);
            }
            points = List.copyOf(points);
        }
    }

    /** 소견 제안. 관련 챕터를 키로 가리킨다. */
    public record ProposedFinding(OutlineFindingType findingType, List<String> chapterKeys, String detail) {

        public ProposedFinding {
            if (findingType == null) {
                throw new IllegalArgumentException("소견 종류는 필수입니다.");
            }
            detail = Strings.requireText(detail, "소견 내용");
            chapterKeys = chapterKeys == null ? List.of() : List.copyOf(chapterKeys);
        }
    }
}
