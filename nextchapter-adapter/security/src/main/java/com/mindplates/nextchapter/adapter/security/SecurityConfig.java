package com.mindplates.nextchapter.adapter.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
 * <p>사용자 체인은 M3(소비 시작)에서 소셜 로그인이 붙는다. 지금은 헬스체크만 열고 나머지는
 * 닫아 둔다 — 기본을 permitAll 로 열어두면 엔드포인트가 늘 때마다 잠그는 것을 잊는 쪽이 기본값이
 * 된다.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, SecretsProperties.class})
public class SecurityConfig {

    private static final String ADMIN_API = "/api/admin/**";
    private static final String ADMIN_LOGIN = "/api/admin/auth/login";

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

    /** {@code roles} 클레임을 {@code ROLE_} 접두사가 붙은 권한으로 옮긴다. 발급 쪽과 짝이다. */
    @Bean
    public JwtAuthenticationConverter adminJwtAuthenticationConverter() {
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
            HttpSecurity http, JwtDecoder jwtDecoder, JwtAuthenticationConverter adminJwtAuthenticationConverter)
            throws Exception {
        return http.securityMatcher(ADMIN_API)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(ADMIN_LOGIN)
                        .permitAll()
                        .anyRequest()
                        .hasRole("ADMIN"))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(adminJwtAuthenticationConverter)))
                // 관리 API 는 브라우저 폼으로 리다이렉트하지 않는다. 401 을 그대로 준다.
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    /** 사용자 체인. 소셜 로그인은 M3 에서 붙는다. */
    @Bean
    public SecurityFilterChain userSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .httpBasic(basic -> {})
                .build();
    }
}
