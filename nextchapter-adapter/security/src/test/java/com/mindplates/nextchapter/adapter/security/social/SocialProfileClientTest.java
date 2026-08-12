package com.mindplates.nextchapter.adapter.security.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.application.user.port.out.SocialProfile;
import com.mindplates.nextchapter.common.exception.AuthenticationFailedException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import java.io.IOException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 제공자별 어댑터가 필요한 이유가 이 테스트에 그대로 나온다 — 정체성 필드와 이메일 위치가 제공자마다
 * 다르다. 그 매핑이 틀리면 로그인은 성공하고 <b>사용자만 갈라진다.</b>
 */
@DisplayName("소셜 프로필 어댑터")
class SocialProfileClientTest {

    private static final String TOKEN_OK = "{\"access_token\":\"provider-token\",\"token_type\":\"bearer\"}";

    @Test
    @DisplayName("Google — 정체성은 sub 이고, 폼에 코드·리다이렉트·시크릿이 실린다")
    void googleMapsSub() throws IOException {
        String userInfo = "{\"sub\":\"113355\",\"email\":\"learner@example.com\",\"name\":\"학습자1\"}";
        try (StubSocialServer stub = StubSocialServer.with(TOKEN_OK, userInfo)) {
            SocialProfile profile = new GoogleSocialProfileClient(properties(stub, SocialProvider.GOOGLE, "secret-1"))
                    .exchange("code-1", "http://localhost:34100/login/google");

            assertThat(profile.provider()).isEqualTo(SocialProvider.GOOGLE);
            assertThat(profile.providerUserId()).isEqualTo("113355");
            assertThat(profile.email()).isEqualTo("learner@example.com");
            assertThat(profile.displayName()).isEqualTo("학습자1");
            assertThat(stub.tokenForm())
                    .contains("grant_type=authorization_code")
                    .contains("code=code-1")
                    .contains("client_id=google-client")
                    .contains("client_secret=secret-1");
            assertThat(stub.header("Authorization")).isEqualTo("Bearer provider-token");
        }
    }

    /** Kakao 는 정체성이 숫자이고 이메일·닉네임이 두 겹 안에 있다. 목으로는 이 차이가 드러나지 않는다. */
    @Test
    @DisplayName("Kakao — 정체성은 id 이고 이메일·닉네임은 kakao_account 안이다")
    void kakaoMapsNestedProfile() throws IOException {
        String userInfo =
                """
                {"id":987654321,"kakao_account":{"email":"learner@kakao.com","profile":{"nickname":"카카오학습자"}}}
                """;
        try (StubSocialServer stub = StubSocialServer.with(TOKEN_OK, userInfo)) {
            SocialProfile profile = new KakaoSocialProfileClient(properties(stub, SocialProvider.KAKAO, null))
                    .exchange("code-1", "http://localhost:34100/login/kakao");

            assertThat(profile.provider()).isEqualTo(SocialProvider.KAKAO);
            assertThat(profile.providerUserId()).isEqualTo("987654321");
            assertThat(profile.email()).isEqualTo("learner@kakao.com");
            assertThat(profile.displayName()).isEqualTo("카카오학습자");
            // 시크릿이 없으면 폼에 넣지 않는다 — 빈 값을 보내면 Kakao 가 거절한다.
            assertThat(stub.tokenForm()).doesNotContain("client_secret");
        }
    }

    /** 이메일은 동의 항목이다. 없다고 실패시키면 사용자는 원인을 알 수 없는 오류를 본다. */
    @Test
    @DisplayName("Kakao — 이메일 동의가 없어도 로그인이 성립한다")
    void kakaoWithoutEmail() throws IOException {
        String userInfo = "{\"id\":987654321,\"kakao_account\":{\"profile\":{\"nickname\":\"닉\"}}}";
        try (StubSocialServer stub = StubSocialServer.with(TOKEN_OK, userInfo)) {
            SocialProfile profile = new KakaoSocialProfileClient(properties(stub, SocialProvider.KAKAO, null))
                    .exchange("code-1", "http://localhost/cb");

            assertThat(profile.email()).isNull();
            assertThat(profile.providerUserId()).isEqualTo("987654321");
        }
    }

    /** 식별자 없는 프로필로 사용자를 만들면 다음 로그인이 남의 계정에 붙는다. */
    @Test
    @DisplayName("식별자가 없는 프로필은 거절한다")
    void rejectsProfileWithoutIdentity() throws IOException {
        try (StubSocialServer stub = StubSocialServer.with(TOKEN_OK, "{\"email\":\"a@b.c\"}")) {
            GoogleSocialProfileClient client =
                    new GoogleSocialProfileClient(properties(stub, SocialProvider.GOOGLE, "secret-1"));

            assertThatThrownBy(() -> client.exchange("code-1", "http://localhost/cb"))
                    .isInstanceOf(AuthenticationFailedException.class);
        }
    }

    /** 토큰 없이 프로필을 조회하면 제공자에 따라 200 과 빈 본문이 오고, 그러면 식별자 없는 사용자가 된다. */
    @Test
    @DisplayName("토큰 응답에 access_token 이 없으면 프로필을 조회하지 않는다")
    void rejectsTokenResponseWithoutAccessToken() throws IOException {
        try (StubSocialServer stub = StubSocialServer.with("{}", "{\"sub\":\"1\"}")) {
            GoogleSocialProfileClient client =
                    new GoogleSocialProfileClient(properties(stub, SocialProvider.GOOGLE, "secret-1"));

            assertThatThrownBy(() -> client.exchange("code-1", "http://localhost/cb"))
                    .isInstanceOf(AuthenticationFailedException.class);
        }
    }

    @Test
    @DisplayName("제공자 오류는 401 로 접힌다 — 응답 본문을 그대로 내보내지 않는다")
    void mapsProviderErrorToAuthenticationFailure() throws IOException {
        String leaky = "{\"error\":\"invalid_client\",\"error_description\":\"client_id=google-client\"}";
        try (StubSocialServer stub = StubSocialServer.with(leaky, 401, "{}", 200)) {
            GoogleSocialProfileClient client =
                    new GoogleSocialProfileClient(properties(stub, SocialProvider.GOOGLE, "secret-1"));

            assertThatThrownBy(() -> client.exchange("code-1", "http://localhost/cb"))
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasMessageNotContaining("google-client");
        }
    }

    @Test
    @DisplayName("프로필 조회 실패도 401 이다")
    void mapsUserInfoErrorToAuthenticationFailure() throws IOException {
        try (StubSocialServer stub = StubSocialServer.with(TOKEN_OK, 200, "{}", 500)) {
            GoogleSocialProfileClient client =
                    new GoogleSocialProfileClient(properties(stub, SocialProvider.GOOGLE, "secret-1"));

            assertThatThrownBy(() -> client.exchange("code-1", "http://localhost/cb"))
                    .isInstanceOf(AuthenticationFailedException.class);
        }
    }

    /** 설정이 없으면 그 제공자만 실패한다. 기본값으로 넘어가면 로그인이 엉뚱한 계정에 붙는다. */
    @Test
    @DisplayName("설정되지 않은 제공자는 호출 전에 거절한다")
    void rejectsUnconfiguredProvider() {
        SocialAuthProperties empty = new SocialAuthProperties(Map.of(), null, null);
        GoogleSocialProfileClient client = new GoogleSocialProfileClient(empty);

        assertThat(empty.isConfigured(SocialProvider.GOOGLE)).isFalse();
        assertThat(empty.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThatThrownBy(() -> client.exchange("code-1", "http://localhost/cb"))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("클라이언트 ID 만 있고 엔드포인트가 없으면 설정된 것이 아니다")
    void partialRegistrationIsNotConfigured() {
        SocialAuthProperties partial = new SocialAuthProperties(
                Map.of(
                        SocialProvider.GOOGLE,
                        new SocialAuthProperties.Registration("id", "secret", null, null, null, null)),
                null,
                null);

        assertThat(partial.isConfigured(SocialProvider.GOOGLE)).isFalse();
        assertThatThrownBy(() -> partial.require(SocialProvider.GOOGLE)).isInstanceOf(InvalidOperationException.class);
    }

    /**
     * 동의 화면 주소를 서버가 조립한다. 클라이언트가 만들면 {@code client_id} 를 프론트엔드에 박아야 하고,
     * 제공자를 추가할 때 서버와 클라이언트를 함께 고쳐야 한다.
     */
    @Test
    @DisplayName("동의 화면 주소에 client_id·redirect_uri·scope·state 가 실린다")
    void buildsAuthorizationUri() throws IOException {
        try (StubSocialServer stub = StubSocialServer.with(TOKEN_OK, "{}")) {
            String uri = new GoogleSocialProfileClient(properties(stub, SocialProvider.GOOGLE, "secret-1"))
                    .authorizationUri("http://localhost:34100/login/google", "state-1");

            assertThat(uri)
                    .contains("/authorize?")
                    .contains("response_type=code")
                    .contains("client_id=google-client")
                    // 콜백 주소는 인코딩돼야 한다 — 날것으로 붙이면 제공자가 파라미터 경계를 잘못 읽는다.
                    .contains("redirect_uri=http%3A%2F%2Flocalhost%3A34100%2Flogin%2Fgoogle")
                    .contains("scope=profile+email")
                    .contains("state=state-1");
        }
    }

    @Test
    @DisplayName("설정되지 않은 제공자의 동의 화면 주소는 만들 수 없다")
    void rejectsAuthorizationUriWhenUnconfigured() {
        GoogleSocialProfileClient client =
                new GoogleSocialProfileClient(new SocialAuthProperties(Map.of(), null, null));

        assertThatThrownBy(() -> client.authorizationUri("http://localhost/cb", "state-1"))
                .isInstanceOf(InvalidOperationException.class);
    }

    private static SocialAuthProperties properties(StubSocialServer stub, SocialProvider provider, String secret) {
        Map<SocialProvider, SocialAuthProperties.Registration> registrations = new EnumMap<>(SocialProvider.class);
        registrations.put(provider, stub.registration(clientIdOf(provider), secret));
        return new SocialAuthProperties(registrations, Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    private static String clientIdOf(SocialProvider provider) {
        return provider == SocialProvider.GOOGLE ? "google-client" : "kakao-client";
    }
}
