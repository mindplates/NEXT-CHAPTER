package com.mindplates.nextchapter.adapter.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * M0 기준선. 인증 경로 두 개(사용자 소셜 / 관리자 자체 계정)는 각각 M3·M1 에서 붙는다.
 *
 * <p>지금은 헬스체크만 열고 나머지는 전부 인증을 요구한다. 반대로 두면 — 기본을 permitAll 로
 * 열어두고 나중에 잠그면 — 엔드포인트가 하나씩 늘 때마다 잠그는 것을 잊는 쪽이 기본값이 된다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> {})
                .build();
    }
}
