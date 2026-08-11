package com.mindplates.nextchapter.domain.catalog.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDateTime;

/**
 * L2 분야 — 도메인 하나에 속한다 (예: 컴퓨터과학 > 인공지능).
 *
 * <p>이 중간 층이 매핑의 후보 수를 수십 개로 유지한다. 분야에는 뼈대가 붙지 않으므로 분야가
 * 갈라지더라도 신호가 분산되지는 않는다 — 그래서 slug 유일성을 도메인 안으로만 요구한다.
 */
public record CatalogField(
        Long id,
        Long domainId,
        String slug,
        String name,
        String description,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public CatalogField {
        if (domainId == null) {
            throw new IllegalArgumentException("분야는 도메인에 속해야 합니다.");
        }
        slug = CatalogSlugs.require(slug);
        name = Strings.requireMaxLength(Strings.requireText(name, "분야 이름"), 120, "분야 이름");
        description = Strings.trimToNull(description);
    }

    public static CatalogField create(Long domainId, String slug, String name, String description, int sortOrder) {
        return new CatalogField(null, domainId, slug, name, description, sortOrder, null, null);
    }

    public CatalogField withDetails(String name, String description, int sortOrder) {
        return new CatalogField(id, domainId, slug, name, description, sortOrder, createdAt, updatedAt);
    }
}
