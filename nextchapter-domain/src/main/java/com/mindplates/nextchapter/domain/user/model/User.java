package com.mindplates.nextchapter.domain.user.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDateTime;

/**
 * 일반 사용자. 사용자 레이어와 신호가 이 정체성에 붙는다.
 *
 * <p>비밀번호가 없다. 공개 서비스라 진입장벽을 낮춰야 하고, 비밀번호 저장·재설정·유출 대응을
 * 떠안지 않기로 했다. 관리자 계정({@code AdminAccount})이 자체 비밀번호를 갖는 것은 가용성
 * 때문이며(소셜 제공자가 죽어도 승인 대기열에 접근해야 한다) 두 타입을 하나로 합치지 않는 이유다.
 *
 * @param providerUserId 제공자가 준 안정 식별자. 이메일이 아니다 — 제공자 쪽에서 이메일을 바꾸면
 *     같은 사람이 다른 계정이 된다
 * @param email 제공자가 주지 않을 수 있다(동의 항목). 없어도 로그인은 성립한다
 */
public record User(
        Long id,
        SocialProvider provider,
        String providerUserId,
        String email,
        String displayName,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** 제공자가 표시 이름을 주지 않았을 때 쓰는 값. 빈 이름으로 저장하면 화면이 빈칸이 된다. */
    private static final String FALLBACK_NAME = "학습자";

    public User {
        if (provider == null) {
            throw new IllegalArgumentException("소셜 제공자는 필수입니다.");
        }
        providerUserId = Strings.requireMaxLength(Strings.requireText(providerUserId, "제공자 사용자 ID"), 200, "제공자 사용자 ID");
        email = Strings.requireMaxLength(Strings.trimToNull(email), 200, "이메일");
        displayName = Strings.requireMaxLength(displayNameOrFallback(displayName), 120, "표시 이름");
    }

    public static User of(SocialProvider provider, String providerUserId, String email, String displayName) {
        return new User(null, provider, providerUserId, email, displayName, null, null, null);
    }

    /**
     * 제공자에서 다시 받아온 프로필을 반영한다. 이름·이메일은 제공자 쪽에서 바뀔 수 있으므로 로그인마다
     * 덮어쓴다 — 최초 로그인 값을 고수하면 화면의 이름이 영원히 옛것이 된다.
     *
     * <p>{@code providerUserId} 는 바꾸지 않는다. 그것이 정체성이고, 바뀌면 다른 사람이다.
     */
    public User withProfile(String newEmail, String newDisplayName, LocalDateTime loginAt) {
        return new User(id, provider, providerUserId, newEmail, newDisplayName, loginAt, createdAt, updatedAt);
    }

    private static String displayNameOrFallback(String value) {
        String trimmed = Strings.trimToNull(value);
        return trimmed == null ? FALLBACK_NAME : trimmed;
    }
}
