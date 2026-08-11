package com.mindplates.nextchapter.domain.catalog.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 카탈로그 노드의 slug 규칙. 세 계층이 같은 규칙을 쓰므로 한 곳에 둔다.
 *
 * <p>slug 는 생성 후 바뀌지 않는 안정 키다. 표시 이름은 {@code name} 으로 고치고 slug 는 그대로
 * 둔다 — slug 가 매핑 프롬프트·미매핑 로그·운영 지표에 문자열로 박히기 때문에, 바뀌면 그 전후
 * 기록을 이어 볼 수 없다.
 */
public final class CatalogSlugs {

    private static final Pattern PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private CatalogSlugs() {}

    public static String require(String slug) {
        String value = Strings.requireText(slug, "slug").toLowerCase(Locale.ROOT);
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("slug 는 소문자·숫자·하이픈만 쓸 수 있습니다: " + slug);
        }
        return value;
    }
}
