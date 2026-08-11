package com.mindplates.nextchapter.application.chapter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.application.chapter.port.out.GraphSyncPort;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * outbox 페이로드 직렬화.
 *
 * <p>도메인 레코드를 직접 직렬화하지 않는다 — 필드 이름을 바꾸는 리팩터링이 <b>토픽에 이미 쌓인
 * 메시지</b>를 읽을 수 없게 만들고, 그 실패는 배포 후 컨슈머가 첫 메시지를 집을 때 드러난다.
 * {@link GraphSyncPort.Edge} 와 짝을 이루는 모양만 유지한다.
 */
public final class OutboxPayloads {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OutboxPayloads() {}

    public static String chapterPersisted(
            Long skeletonId, Long chapterId, String chapterKey, String title, int version) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skeletonId", skeletonId);
        payload.put("chapterId", chapterId);
        payload.put("chapterKey", chapterKey);
        payload.put("title", title);
        payload.put("version", version);
        return write(payload);
    }

    public static String chapterGraphPersisted(Long skeletonId, List<ChapterRelation> relations) {
        List<Map<String, Object>> edges = relations.stream()
                .map(relation -> Map.<String, Object>of(
                        "from", relation.fromChapterId(),
                        "to", relation.toChapterId(),
                        "type", relation.relationType().name()))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skeletonId", skeletonId);
        payload.put("edges", edges);
        return write(payload);
    }

    private static String write(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new InvalidOperationException(
                    "outbox 페이로드를 만들지 못했습니다: " + e.getClass().getSimpleName());
        }
    }
}
