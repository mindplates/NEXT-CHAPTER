package com.mindplates.nextchapter.adapter.security;

import com.mindplates.nextchapter.adapter.security.social.SocialAuthProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * 인증 경로를 둘로 나눈다 — 관리자(자체 계정 → JWT)와 일반 사용자(소셜 → JWT).
 *
 * <p>필터 체인을 두 개 두는 것이 그 분리의 실체다. 하나의 체인에 규칙을 섞으면 사용자 쪽 설정을
 * 고칠 때 관리자 경로가 함께 흔들리고, 소셜 제공자 설정 하나가 잘못돼도 승인 대기열과 비용 상한에
 * 접근하지 못하는 상태가 만들어진다. 그 상태는 받아들일 수 없다는 것이 분리의 이유였다.
 *
 * <p>두 체인 모두 <b>무상태 Bearer 토큰</b>이라 어느 쪽도 CSRF 를 켜지 않는다. 자격 증명이 {@code
 * Authorization} 헤더에 있고 세션도 쿠키도 만들지 않으므로, 브라우저가 자동으로 실어 보내는 자격
 * 증명이 없다 — CSRF 는 그 자동 전송을 전제로 하는 공격이라 성립하지 않는다. 사용자 쪽을 쿠키로
 * 두지 않은 이유는 리프레시 쿠키를 두는 순간 자동 전송되는 자격 증명이 생겨 이 체인에도 CSRF 방어가
 * 필요해지고, 그 방어는 웹뿐 아니라 앱 클라이언트에도 규칙을 하나 더 얹기 때문이다.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, SecretsProperties.class, SocialAuthProperties.class})
public class SecurityConfig {

    private static final String ADMIN_API = "/api/admin/**";
    private static final String ADMIN_LOGIN = "/api/admin/auth/login";

    /** 사용자 API. 이 아래는 {@code USER} 역할을 요구한다. */
    private static final String USER_API = "/api/**";

    /** 로그인 자체는 토큰 없이 되어야 한다 — 여기서 토큰을 받는다. */
    private static final String SOCIAL_LOGIN = "/api/auth/social/*";

    /** 지원 제공자 목록. 로그인 화면이 버튼을 그리기 위해 인증 전에 읽는다. */
    private static final String SOCIAL_PROVIDERS = "/api/auth/providers";

    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        return JwtSecretKeyFactory.create(properties.secret());
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * {@code roles} 클레임을 {@code ROLE_} 접두사가 붙은 권한으로 옮긴다. 발급 쪽과 짝이다.
     *
     * <p>두 체인이 같은 변환기를 공유한다. 변환 규칙이 아니라 <b>요구하는 역할</b>이 체인마다 다르고,
     * 규칙을 복제하면 한쪽만 고쳐 클레임 이름이 어긋나는 순간 그 체인의 모든 토큰이 권한 없는 토큰이 된다.
     */
    @Bean
    public JwtAuthenticationConverter rolesJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(AdminJwtTokenIssuer.ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * 관리자 API 체인.
     *
     * <p>CSRF 를 끈 이유는 이 체인이 쿠키를 전혀 쓰지 않기 때문이다 — 자격 증명이 {@code
     * Authorization} 헤더의 Bearer 토큰이고 세션도 만들지 않으므로, 브라우저가 자동으로 실어
     * 보내는 자격 증명이 없다. CSRF 는 그 자동 전송을 전제로 하는 공격이라 성립하지 않는다.
     * 반대로 사용자 체인은 쿠키를 쓸 수 있으므로 거기서는 켜 둔다.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain adminSecurityFilterChain(
            HttpSecurity http, JwtDecoder jwtDecoder, JwtAuthenticationConverter rolesJwtAuthenticationConverter)
            throws Exception {
        return http.securityMatcher(ADMIN_API)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(ADMIN_LOGIN)
                        .permitAll()
                        .anyRequest()
                        .hasRole("ADMIN"))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(rolesJwtAuthenticationConverter)))
                // 관리 API 는 브라우저 폼으로 리다이렉트하지 않는다. 401 을 그대로 준다.
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    /**
     * 사용자 체인.
     *
     * <p>{@code /api/**} 에 {@code USER} 역할을 요구하는 것이 요점이다. {@code authenticated()} 만
     * 두면 관리자 토큰도 통과하는데, 사용자 API 는 토큰 subject 를 사용자 ID 로 읽으므로 관리자 토큰이
     * 들어오면 이메일을 ID 로 파싱하다 실패한다 — 500 으로 나타나는 인가 구멍이다.
     *
     * <p>관리 경로는 이 체인에 오지 않는다. 관리자 체인이 {@code /api/admin/**} 를 먼저 잡는다.
     */
    @Bean
    public SecurityFilterChain userSecurityFilterChain(
            HttpSecurity http, JwtDecoder jwtDecoder, JwtAuthenticationConverter rolesJwtAuthenticationConverter)
            throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, SOCIAL_LOGIN)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, SOCIAL_PROVIDERS)
                        .permitAll()
                        .requestMatchers(USER_API)
                        .hasRole(UserJwtTokenIssuer.ROLE_USER)
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(rolesJwtAuthenticationConverter)))
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }
}
