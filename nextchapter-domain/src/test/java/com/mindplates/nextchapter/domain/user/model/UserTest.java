package com.mindplates.nextchapter.domain.user.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 사용자가 지켜야 하는 것은 <b>정체성이 제공자 식별자라는 사실</b>이다. 이메일을 정체성으로 쓰면
 * 제공자 쪽에서 이메일을 바꾼 사용자가 새 계정을 받고, 그 순간 그 사람의 학습 레이어가 끊긴다.
 */
@DisplayName("사용자")
class UserTest {

    @Test
    @DisplayName("제공자 식별자가 없으면 거절한다")
    void requiresProviderIdentity() {
        assertThatThrownBy(() -> User.of(SocialProvider.GOOGLE, " ", "a@b.c", "이름"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.of(null, "sub-1", "a@b.c", "이름")).isInstanceOf(IllegalArgumentException.class);
    }

    /** Kakao 는 이메일이 동의 항목이다. 없다고 거절하면 사용자는 원인을 알 수 없는 실패를 본다. */
    @Test
    @DisplayName("이메일이 없어도 사용자가 된다")
    void emailIsOptional() {
        User user = User.of(SocialProvider.KAKAO, "12345", null, "카카오사용자");

        assertThat(user.email()).isNull();
        assertThat(user.providerUserId()).isEqualTo("12345");
    }

    @Test
    @DisplayName("표시 이름이 없으면 기본 이름을 쓴다 — 빈칸으로 저장하지 않는다")
    void fallsBackDisplayName() {
        assertThat(User.of(SocialProvider.GOOGLE, "sub-1", null, "  ").displayName())
                .isEqualTo("학습자");
    }

    @Test
    @DisplayName("프로필 갱신은 이름·이메일·로그인 시각만 바꾼다")
    void profileUpdateKeepsIdentity() {
        LocalDateTime at = LocalDateTime.of(2026, 8, 12, 9, 0);
        User user = new User(7L, SocialProvider.GOOGLE, "sub-1", "old@b.c", "옛이름", null, at, at);

        User updated = user.withProfile("new@b.c", "새이름", at.plusDays(1));

        assertThat(updated.id()).isEqualTo(7L);
        assertThat(updated.provider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(updated.providerUserId()).isEqualTo("sub-1");
        assertThat(updated.email()).isEqualTo("new@b.c");
        assertThat(updated.displayName()).isEqualTo("새이름");
        assertThat(updated.lastLoginAt()).isEqualTo(at.plusDays(1));
        assertThat(updated.createdAt()).isEqualTo(at);
    }

    @Test
    @DisplayName("갱신은 새 객체를 만든다")
    void updateIsImmutable() {
        User user = new User(7L, SocialProvider.GOOGLE, "sub-1", "old@b.c", "옛이름", null, null, null);

        user.withProfile("new@b.c", "새이름", LocalDateTime.now());

        assertThat(user.displayName()).isEqualTo("옛이름");
    }

    @Test
    @DisplayName("표시 이름과 이메일은 길이 제한을 넘을 수 없다")
    void rejectsOverlongFields() {
        assertThatThrownBy(() -> User.of(SocialProvider.GOOGLE, "sub-1", "e".repeat(201), "이름"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.of(SocialProvider.GOOGLE, "sub-1", "a@b.c", "이".repeat(121)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
