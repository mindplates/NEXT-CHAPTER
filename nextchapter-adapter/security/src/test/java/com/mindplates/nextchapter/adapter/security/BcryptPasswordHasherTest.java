package com.mindplates.nextchapter.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("비밀번호 해싱")
class BcryptPasswordHasherTest {

    private final BcryptPasswordHasher hasher = new BcryptPasswordHasher(new SecurityConfig().passwordEncoder());

    @Test
    @DisplayName("해시에 알고리즘 접두사가 붙는다 — 나중에 알고리즘을 올릴 유일한 이행 경로다")
    void hashCarriesAlgorithmPrefix() {
        assertThat(hasher.hash("long-enough-password")).startsWith("{bcrypt}");
    }

    @Test
    @DisplayName("같은 비밀번호도 해시가 매번 다르다 — 솔트가 들어간다")
    void hashIsSalted() {
        assertThat(hasher.hash("long-enough-password")).isNotEqualTo(hasher.hash("long-enough-password"));
    }

    @Test
    @DisplayName("맞는 비밀번호만 통과한다")
    void matchesOnlyCorrectPassword() {
        String hash = hasher.hash("long-enough-password");

        assertThat(hasher.matches("long-enough-password", hash)).isTrue();
        assertThat(hasher.matches("wrong-password", hash)).isFalse();
    }

    @Test
    @DisplayName("평문이 해시에 남지 않는다")
    void hashDoesNotContainPlaintext() {
        assertThat(hasher.hash("secret-password")).doesNotContain("secret-password");
    }
}
