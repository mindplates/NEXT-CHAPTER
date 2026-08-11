package com.mindplates.nextchapter.domain.catalog.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDateTime;

/**
 * L1 도메인 — 카탈로그의 최상위 계층 (예: 컴퓨터과학).
 *
 * <p>3단계로 나눈 이유는 LLM 매핑을 층별 좁히기로 만들기 위해서다. 2단계면 도메인 하나에 주제가
 * 수백 개 붙어 후보 목록이 프롬프트에 들어가지 않는다.
 */
public record CatalogDomain(
        Long id,
        String slug,
        String name,
        String description,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public CatalogDomain {
        slug = CatalogSlugs.require(slug);
        name = Strings.requireMaxLength(Strings.requireText(name, "도메인 이름"), 120, "도메인 이름");
        description = Strings.trimToNull(description);
    }

    public static CatalogDomain create(String slug, String name, String description, int sortOrder) {
        return new CatalogDomain(null, slug, name, description, sortOrder, null, null);
    }

    /** 표시 정보만 고친다. slug 는 안정 키라 여기서 바뀌지 않는다. */
    public CatalogDomain withDetails(String name, String description, int sortOrder) {
        return new CatalogDomain(id, slug, name, description, sortOrder, createdAt, updatedAt);
    }
}
