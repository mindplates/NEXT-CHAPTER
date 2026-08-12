package com.mindplates.nextchapter.domain.chapter.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 블록 ID 의 생성·승계 규칙. <b>이 규칙이 집단 루프의 정밀도를 결정한다.</b>
 *
 * <p>신호는 챕터가 아니라 블록에 붙는다. 그래서 "여러 사람이 같은 지점에서 묻는다"의 <i>지점</i>이
 * 문단·수식 단위로 특정되고, 수정 전후 오답률을 같은 블록에서 비교할 수 있다. 그 비교가 성립하려면
 * ID 가 두 가지를 만족해야 한다.
 *
 * <ol>
 *   <li><b>버전이 올라가도 유지된다</b> — 수정으로 ID 가 전부 갈리면 수정 전후 신호를 이어 볼 수 없다
 *   <li><b>재사용되지 않는다</b> — v1 의 {@code b3} 가 v5 에서 다른 내용이 되면 두 버전의 신호가
 *       조용히 한 블록에 섞인다. 이쪽이 더 위험하다: 에러 없이 잘못된 결론을 만든다
 * </ol>
 *
 * <p>그래서 발급은 챕터에 저장된 <b>단조 증가 카운터</b>가 한다. 삭제된 블록의 ID 는 비어 있는 채로
 * 남고 다시 쓰이지 않는다.
 *
 * <p>승계는 수정 프롬프트에 기존 블록 문서를 ID 와 함께 넣고 "유지되는 블록은 같은 ID 를 그대로
 * 쓰라"고 지시해 얻는다. 모델이 돌려준 ID 가 기존 집합에 있으면 승계하고, 없거나 형식이 틀리거나
 * 문서 안에서 중복이면 <b>버리고 새로 발급한다.</b> 잘못된 ID 를 통과시키는 것보다 신호가 새 블록에서
 * 다시 시작하는 편이 낫다 — 전자는 다른 블록의 신호를 오염시킨다.
 *
 * <p>이 규칙이 실제로 이어지는지의 <b>검증</b>은 M5 P5.7 이다. 규칙을 지금 정하는 이유는, 이미 쌓인
 * 콘텐츠의 ID 를 나중에 다시 매길 방법이 없기 때문이다.
 */
public final class BlockIds {

    /**
     * 공용 본문 블록과 개인화 보충 블록의 <b>ID 공간을 나눈다.</b>
     *
     * <p>같은 공간을 쓰면 어떤 사용자의 보충 블록 {@code b7} 에 붙은 신호가 공용 본문의 {@code b7} 신호와
     * 섞인다. 집단 루프는 <i>같은 본문에 대한 반응</i>을 겹쳐 보는 것으로 성립하므로, 그 오염은 임계치
     * 판정과 수정 전후 비교를 동시에 무너뜨린다 — 그리고 에러 없이 일어난다.
     */
    private static final Pattern PATTERN = Pattern.compile("([bs])(\\d+)");

    private static final String BODY_PREFIX = "b";

    private static final String SUPPLEMENT_PREFIX = "s";

    private BlockIds() {}

    /** 카운터 값에 대응하는 본문 블록 ID. */
    public static String of(int sequence) {
        return prefixed(BODY_PREFIX, sequence);
    }

    /** 보충 블록 ID. 공용 본문과 다른 공간이라 신호가 섞이지 않는다. */
    public static String ofSupplement(int sequence) {
        return prefixed(SUPPLEMENT_PREFIX, sequence);
    }

    public static boolean isValid(String blockId) {
        return blockId != null && PATTERN.matcher(blockId).matches();
    }

    /** 공용 본문 블록 ID 인지. 블록 문서(공용)는 이것만 담는다. */
    public static boolean isBody(String blockId) {
        return isValid(blockId) && blockId.startsWith(BODY_PREFIX);
    }

    public static boolean isSupplement(String blockId) {
        return isValid(blockId) && blockId.startsWith(SUPPLEMENT_PREFIX);
    }

    private static String prefixed(String prefix, int sequence) {
        if (sequence < 1) {
            throw new IllegalArgumentException("블록 ID 순번은 1 이상이어야 합니다: " + sequence);
        }
        return prefix + sequence;
    }

    /**
     * ID 에서 순번을 읽는다. 카운터가 뒤처져 있을 때(예: 데이터 이관 후) 따라잡는 데 쓴다 —
     * 카운터가 기존 최대값보다 작으면 다음 발급이 이미 쓰인 ID 를 다시 내주게 된다.
     */
    public static int sequenceOf(String blockId) {
        Matcher matcher = PATTERN.matcher(blockId);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("블록 ID 형식이 아닙니다: " + blockId);
        }
        // group(1) 은 접두사(b/s)이고 순번은 group(2) 다.
        return Integer.parseInt(matcher.group(2));
    }
}
