package com.mindplates.nextchapter.application.generation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterOutline;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelation;
import com.mindplates.nextchapter.domain.chapter.model.OutlineFindingType;
import com.mindplates.nextchapter.domain.chapter.model.ProposedOutlineSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ② 개요 생성 프롬프트와 응답 파싱. */
final class ChapterOutlinePrompts {

    private static final Logger log = LoggerFactory.getLogger(ChapterOutlinePrompts.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * "겹치면 보고하라"를 지시에 넣는다. 겹침을 만들지 말라고만 하면 모델은 겹침을 <b>숨긴다</b> —
     * 지시를 지킨 것처럼 보이는 응답을 만드는 쪽이 쉬우니까. 보고 항목을 응답 형식에 두면 겹침이
     * 드러나고, 그것이 이 단계의 존재 이유다.
     */
    static final String SYSTEM_PROMPT =
            """
            너는 교육 콘텐츠 설계자다. 확정된 챕터 그래프를 받아 챕터별 개요를 쓴다.

            반드시 지킬 것:
            - 챕터 하나도 빠뜨리지 말고, 한 챕터에 개요를 두 번 쓰지 마라.
            - 개요는 본문이 다뤄야 할 항목 목록이다. 본문을 쓰지 말고 항목만 쓴다.
            - 챕터마다 항목 %d~%d개.
            - 다른 챕터가 다룰 내용을 끌어오지 마라. 그래도 겹치는 부분이 남으면 findings 에 보고하라.
            - 주제 범위에서 빠진 내용이 보이면 findings 에 gap 으로 보고하라. 챕터를 임의로 추가하지는 마라.

            응답은 JSON 객체 하나만 출력한다. 설명·마크다운·코드펜스를 붙이지 않는다.
            """
                    .formatted(ChapterOutline.MIN_POINTS, ChapterOutline.MAX_POINTS);

    private ChapterOutlinePrompts() {}

    static String prompt(String topicName, List<Chapter> chapters, List<ChapterRelation> relations) {
        Map<Long, String> keysById = chapters.stream().collect(Collectors.toMap(Chapter::id, Chapter::chapterKey));
        String chapterList = chapters.stream()
                .map(chapter -> "- " + chapter.chapterKey() + " : " + chapter.title()
                        + (chapter.summary() == null ? "" : " — " + chapter.summary()))
                .collect(Collectors.joining("\n"));
        String relationList = relations.stream()
                .filter(relation ->
                        keysById.containsKey(relation.fromChapterId()) && keysById.containsKey(relation.toChapterId()))
                .map(relation -> "- " + keysById.get(relation.fromChapterId()) + " → "
                        + keysById.get(relation.toChapterId()) + " ("
                        + relation.relationType().name().toLowerCase(Locale.ROOT) + ")")
                .collect(Collectors.joining("\n"));

        return """
                주제: %s

                챕터:
                %s

                챕터 간 관계:
                %s

                각 챕터의 개요를 쓰고, 챕터 간 중복·누락을 함께 보고하라.

                응답 형식:
                {
                  "outlines": [
                    {"chapterKey": "gradient-descent", "points": ["경사의 정의", "갱신 규칙", "수렴 조건"]}
                  ],
                  "findings": [
                    {"type": "duplicate", "chapterKeys": ["a", "b"], "detail": "두 챕터가 학습률을 모두 설명한다"},
                    {"type": "gap", "chapterKeys": [], "detail": "정규화를 다루는 챕터가 없다"}
                  ]
                }
                """
                .formatted(topicName, chapterList, relationList.isEmpty() ? "- (없음)" : relationList);
    }

    /**
     * 파싱 실패를 올린다 — 그래프 생성과 같은 이유다. 개요가 없으면 본문을 쓸 재료가 없고, 조용히
     * 넘어가면 빈 본문이 생성된다.
     */
    static ProposedOutlineSet parse(String response) {
        JsonNode root = readObject(response);

        List<ProposedOutlineSet.ProposedOutline> outlines = new ArrayList<>();
        for (JsonNode node : root.path("outlines")) {
            List<String> points = new ArrayList<>();
            node.path("points").forEach(point -> points.add(point.asText(null)));
            outlines.add(new ProposedOutlineSet.ProposedOutline(
                    node.path("chapterKey").asText(null), points));
        }

        List<ProposedOutlineSet.ProposedFinding> findings = new ArrayList<>();
        for (JsonNode node : root.path("findings")) {
            OutlineFindingType type = findingType(node.path("type").asText(null));
            if (type == null) {
                log.warn("[개요] 알 수 없는 소견 종류를 건너뛴다: {}", node.path("type").asText(null));
                continue;
            }
            List<String> keys = new ArrayList<>();
            node.path("chapterKeys").forEach(key -> keys.add(key.asText(null)));
            findings.add(new ProposedOutlineSet.ProposedFinding(
                    type, keys, node.path("detail").asText(null)));
        }
        return new ProposedOutlineSet(outlines, findings);
    }

    private static OutlineFindingType findingType(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return OutlineFindingType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static JsonNode readObject(String response) {
        if (response == null || response.isBlank()) {
            throw new ExternalApiException("개요 생성 응답이 비어 있습니다.");
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ExternalApiException("개요 생성 응답에서 JSON 객체를 찾을 수 없습니다.");
        }
        try {
            return MAPPER.readTree(response.substring(start, end + 1));
        } catch (Exception e) {
            throw new ExternalApiException(
                    "개요 생성 응답을 파싱하지 못했습니다: " + e.getClass().getSimpleName());
        }
    }
}
