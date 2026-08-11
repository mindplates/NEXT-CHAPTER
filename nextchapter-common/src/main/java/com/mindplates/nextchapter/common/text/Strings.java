package com.mindplates.nextchapter.common.text;

import java.text.Normalizer;
import java.util.Locale;

/** 문자열 정리 · 검증의 단일 지점. 같은 로직이 도메인마다 복제되는 것을 막는다. */
public final class Strings {

    /** 비교에서 무시할 문자 — 공백과 구두점. "기계 학습"과 "기계학습"이 같은 값이 되게 한다. */
    private static final String STRIPPED = "[\\s\\p{Punct}]+";

    private Strings() {}

    /** 공백만 있거나 비어 있으면 예외. 통과하면 trim 한 값을 돌려준다. */
    public static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "은(는) 필수입니다.");
        }
        return value.trim();
    }

    /** null·공백이면 null, 아니면 trim 한 값. 선택 입력 컬럼을 빈 문자열로 오염시키지 않기 위한 것. */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String requireMaxLength(String value, int maxLength, String label) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(label + "은(는) " + maxLength + "자를 넘을 수 없습니다.");
        }
        return value;
    }

    /**
     * 자유 입력 비교용 정규화. 별칭 대조("기계학습" vs "기계 학습")가 표기 차이로 실패하지
     * 않게 한다.
     *
     * <p>NFKC 를 쓰는 이유는 한글 자모 분리형과 결합형, 전각·반각 영문이 코드포인트로는 다르지만
     * 사용자에게는 같은 글자이기 때문이다. 그 차이로 별칭이 빗나가면 같은 주제에 뼈대가 두 개
     * 생긴다 — 정규화 실패의 대가가 신호 분산이라 여기서 넉넉하게 접는다.
     */
    public static String normalizeForMatch(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll(STRIPPED, "");
        return normalized.isEmpty() ? null : normalized;
    }
}
