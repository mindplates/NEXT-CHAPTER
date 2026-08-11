package com.mindplates.nextchapter.domain.admin.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDateTime;

/**
 * 벤더별 API 키. 저장·전달되는 값은 <b>암호문</b>이다.
 *
 * <p>평문 필드를 두지 않는 것이 요점이다. 도메인 모델에 평문이 있으면 로그·에러 메시지·직렬화 중
 * 어디로든 새어 나가고, 그 경로는 하나만 놓쳐도 유출이 된다. 복호화는 실제 호출 직전 한 지점에서만
 * 일어난다.
 *
 * @param keyHint 화면 표시용 뒤 4자. 운영자가 "지금 어느 키가 들어 있는지"를 확인하지 못하면 키
 *     교체 작업 자체가 성립하지 않는다
 */
public record AiCredential(
        Long id,
        AiVendor vendor,
        String apiKeyCipher,
        String keyHint,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    private static final int HINT_LENGTH = 4;

    public AiCredential {
        if (vendor == null) {
            throw new IllegalArgumentException("벤더는 필수입니다.");
        }
        apiKeyCipher = Strings.requireText(apiKeyCipher, "API 키");
        keyHint = Strings.requireText(keyHint, "키 힌트");
    }

    public static AiCredential of(AiVendor vendor, String apiKeyCipher, String keyHint) {
        return new AiCredential(null, vendor, apiKeyCipher, keyHint, null, null);
    }

    /**
     * 평문 키에서 표시용 힌트를 만든다. 짧은 키는 힌트도 짧게 — 길이를 맞추려고 앞자리를 노출하면
     * 그게 곧 키의 일부를 흘리는 일이 된다.
     */
    public static String hintOf(String rawApiKey) {
        String key = Strings.requireText(rawApiKey, "API 키");
        int from = Math.max(0, key.length() - HINT_LENGTH);
        return "…" + key.substring(from);
    }
}
