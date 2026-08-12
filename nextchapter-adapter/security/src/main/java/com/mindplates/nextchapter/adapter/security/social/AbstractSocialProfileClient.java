package com.mindplates.nextchapter.adapter.security.social;

import com.mindplates.nextchapter.adapter.security.social.SocialAuthProperties.Registration;
import com.mindplates.nextchapter.application.user.port.out.SocialProfile;
import com.mindplates.nextchapter.application.user.port.out.SocialProfileClientPort;
import com.mindplates.nextchapter.common.exception.AuthenticationFailedException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 두 단계는 제공자마다 같다 — 인가 코드를 액세스 토큰으로 바꾸고, 그 토큰으로 프로필을 읽는다.
 * 다른 것은 <b>응답 JSON 의 필드 이름</b>뿐이므로 그 한 곳만 하위 클래스에 남긴다.
 *
 * <p>실패 응답을 사용자에게 그대로 내보내지 않는다. 제공자의 오류 본문에는 client_id 와 요청 파라미터가
 * 섞여 들어오고, 그중 하나가 우리 설정값이다. 상세는 로그에만 남긴다.
 */
abstract class AbstractSocialProfileClient implements SocialProfileClientPort {

    private static final Logger log = LoggerFactory.getLogger(AbstractSocialProfileClient.class);

    /** 실패 사유를 나누지 않는다 — "코드가 만료됐다"와 "설정이 틀렸다"를 응답으로 구분해 줄 이유가 없다. */
    private static final String LOGIN_FAILED = "소셜 로그인에 실패했습니다. 다시 시도해 주세요.";

    private final SocialAuthProperties properties;
    private final RestClient restClient;

    AbstractSocialProfileClient(SocialAuthProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build());
        factory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 동의 화면 주소를 조립한다. 파라미터 이름은 OAuth2 표준이라 제공자별로 다르지 않다 — 다른 것은
     * 엔드포인트와 scope 이고, 둘 다 설정값이다.
     */
    @Override
    public final String authorizationUri(String redirectUri, String state) {
        Registration registration = properties.require(provider());
        StringBuilder uri = new StringBuilder(registration.authorizationUri())
                .append(registration.authorizationUri().contains("?") ? "&" : "?")
                .append("response_type=code")
                .append("&client_id=")
                .append(encode(registration.clientId()))
                .append("&redirect_uri=")
                .append(encode(redirectUri));
        if (registration.scope() != null && !registration.scope().isBlank()) {
            uri.append("&scope=").append(encode(registration.scope()));
        }
        if (state != null && !state.isBlank()) {
            uri.append("&state=").append(encode(state));
        }
        return uri.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public final SocialProfile exchange(String authorizationCode, String redirectUri) {
        Registration registration = properties.require(provider());
        String accessToken = requestAccessToken(registration, authorizationCode, redirectUri);
        return toProfile(fetchUserInfo(registration, accessToken));
    }

    /** 제공자 응답에서 정체성·이메일·표시 이름을 뽑는다. 여기가 제공자별로 유일하게 다른 지점이다. */
    protected abstract SocialProfile toProfile(Map<String, Object> userInfo);

    private String requestAccessToken(Registration registration, String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", registration.clientId());
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        if (registration.clientSecret() != null && !registration.clientSecret().isBlank()) {
            form.add("client_secret", registration.clientSecret());
        }

        Map<String, Object> response = post(registration.tokenUri(), form);
        Object accessToken = response.get("access_token");
        if (!(accessToken instanceof String token) || token.isBlank()) {
            // 토큰 없이 진행하면 프로필 조회가 익명으로 나가고, 제공자에 따라 200 과 빈 본문이 온다.
            log.warn("[소셜 로그인] {} 토큰 응답에 access_token 이 없다", provider());
            throw new AuthenticationFailedException(LOGIN_FAILED);
        }
        return token;
    }

    private Map<String, Object> post(String uri, MultiValueMap<String, String> form) {
        try {
            return asMap(restClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class));
        } catch (InvalidOperationException | AuthenticationFailedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[소셜 로그인] {} 토큰 교환 실패: {}", provider(), e.getClass().getSimpleName());
            throw new AuthenticationFailedException(LOGIN_FAILED);
        }
    }

    private Map<String, Object> fetchUserInfo(Registration registration, String accessToken) {
        try {
            return asMap(restClient
                    .get()
                    .uri(registration.userInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class));
        } catch (Exception e) {
            log.warn("[소셜 로그인] {} 프로필 조회 실패: {}", provider(), e.getClass().getSimpleName());
            throw new AuthenticationFailedException(LOGIN_FAILED);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object body) {
        return body instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    /** 중첩 객체에서 값을 꺼낸다. Kakao 처럼 프로필이 두 겹 안에 있는 응답을 다루기 위한 것. */
    @SuppressWarnings("unchecked")
    protected static Map<String, Object> nested(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    protected static String text(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /** 정체성이 없는 프로필은 사용자로 만들 수 없다. 빈 값으로 저장하면 다음 로그인이 남의 계정에 붙는다. */
    protected String requireIdentity(String providerUserId) {
        if (providerUserId == null || providerUserId.isBlank()) {
            log.warn("[소셜 로그인] {} 프로필에 식별자가 없다", provider());
            throw new AuthenticationFailedException(LOGIN_FAILED);
        }
        return providerUserId;
    }
}
