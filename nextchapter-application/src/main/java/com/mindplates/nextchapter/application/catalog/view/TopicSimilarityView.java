package com.mindplates.nextchapter.application.catalog.view;

/**
 * 유사도 검색 결과 한 건.
 *
 * @param score 코사인 유사도. 1에 가까울수록 비슷하다 (거리 = 1 - score)
 */
public record TopicSimilarityView(
        Long topicId, String slug, String name, Long fieldId, String fieldName, String domainName, double score) {}
