package com.mindplates.nextchapter.application.generation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import com.mindplates.nextchapter.domain.chapter.model.ProposedChapter;
import com.mindplates.nextchapter.domain.chapter.model.ProposedChapterGraph;
import com.mindplates.nextchapter.domain.chapter.model.ProposedRelation;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ① 그래프 생성 프롬프트와 응답 파싱. */
final class SkeletonGraphPrompts {

    private static final Logger log = LoggerFactory.getLogger(SkeletonGraphPrompts.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * "선형 목차를 만들지 말라"를 명시한다. 지시하지 않으면 모델은 거의 항상 1장·2장·3장 형태의 목차를
     * 내놓는다 — 그건 이 제품이 그래프를 택한 이유를 정확히 거스른다.
     */
    static final String SYSTEM_PROMPT =
            """
            너는 교육 콘텐츠 설계자다. 주어진 주제의 전 범위를 챕터로 나누고 챕터 간 관계를 설계한다.

            반드시 지킬 것:
            - 선형 목차를 만들지 마라. 챕터는 서로 연관 관계를 갖는 그래프이고, "다음 장" 같은 순서 관계는 쓰지 않는다.
            - 선행 관계(prerequisite)는 정말로 먼저 알아야 하는 경우에만 쓴다. 남용하면 그래프가 한 줄이 된다.
            - 선행 관계에 순환을 만들지 마라. A 가 B 의 선행이면 B 는 A 의 선행이 될 수 없다.
            - 선행 관계가 없는 챕터가 최소 하나는 있어야 한다 (진입점).
            - 챕터 키는 소문자 영문·숫자·하이픈만 쓴다. 제목과 요약은 한국어로 쓴다.

            응답은 JSON 객체 하나만 출력한다. 설명·마크다운·코드펜스를 붙이지 않는다.
            """;

    private SkeletonGraphPrompts() {}

    static String prompt(String topicName, String topicDescription, String fieldName, String domainName) {
        return """
                주제: %s
                분류: %s > %s > %s
                %s

                이 주제의 전 범위를 챕터 %d~%d개로 나누고, 챕터 간 관계를 설계하라.

                관계 타입:
                - prerequisite : 앞 챕터를 이해해야 뒤 챕터를 이해할 수 있다 (단단한 의존)
                - related      : 서로 도움이 되지만 순서는 없다
                - deepens      : 뒤 챕터가 앞 챕터의 심화다

                응답 형식:
                {
                  "chapters": [
                    {"key": "gradient-descent", "title": "경사하강법", "summary": "손실을 줄이는 방향으로 …"}
                  ],
                  "relations": [
                    {"from": "loss-function", "to": "gradient-descent", "type": "prerequisite"}
                  ]
                }
                """
                .formatted(
                        topicName,
                        domainName,
                        fieldName,
                        topicName,
                        topicDescription == null ? "" : "주제 설명: " + topicDescription,
                        ProposedChapterGraph.MIN_CHAPTERS,
                        ProposedChapterGraph.MAX_CHAPTERS);
    }

    /**
     * 파싱 실패를 {@link ExternalApiException} 으로 올린다 — 매핑 단계와 다른 선택이다.
     *
     * <p>주제 매핑에서는 실패를 "매칭 실패"로 흘려보냈다. 사용자가 후보 제시 경로로 갈 수 있었기
     * 때문이다. 그래프 생성에는 그런 대안 경로가 없다. 조용히 빈 그래프를 만들면 챕터 0개짜리 뼈대가
     * 다음 단계로 넘어가고, 완료 판정이 즉시 참이 되어 빈 뼈대가 published 로 간다.
     */
    static ProposedChapterGraph parse(String response) {
        JsonNode root = readObject(response);
        List<ProposedChapter> chapters = new ArrayList<>();
        int order = 0;
        for (JsonNode node : root.path("chapters")) {
            chapters.add(new ProposedChapter(
                    node.path("key").asText(null),
                    node.path("title").asText(null),
                    node.path("summary").asText(null),
                    order++));
        }

        List<ProposedRelation> relations = new ArrayList<>();
        for (JsonNode node : root.path("relations")) {
            ChapterRelationType type = relationType(node.path("type").asText(null));
            if (type == null) {
                log.warn("[그래프] 알 수 없는 관계 타입을 건너뛴다: {}", node.path("type").asText(null));
                continue;
            }
            relations.add(new ProposedRelation(
                    node.path("from").asText(null), node.path("to").asText(null), type));
        }
        return new ProposedChapterGraph(chapters, relations);
    }

    private static ChapterRelationType relationType(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return ChapterRelationType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static JsonNode readObject(String response) {
        if (response == null || response.isBlank()) {
            throw new ExternalApiException("그래프 생성 응답이 비어 있습니다.");
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ExternalApiException("그래프 생성 응답에서 JSON 객체를 찾을 수 없습니다.");
        }
        try {
            return MAPPER.readTree(response.substring(start, end + 1));
        } catch (Exception e) {
            throw new ExternalApiException(
                    "그래프 생성 응답을 파싱하지 못했습니다: " + e.getClass().getSimpleName());
        }
    }
}
