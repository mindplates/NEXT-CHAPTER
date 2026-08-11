package com.mindplates.nextchapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 최초 관리자 계정 설정. 계정이 하나도 없을 때만 쓰인다.
 *
 * <p>기본 비밀번호를 두지 않는다. 값이 없으면 계정을 만들지 않고 경고만 남긴다 — 알려진 기본
 * 비밀번호를 가진 관리자 계정이 자동으로 생기는 것보다, 계정이 없어 운영자가 설정을 채우는 편이
 * 낫다.
 *
 * @param email 최초 관리자 이메일
 * @param name 표시 이름
 * @param password 최초 비밀번호. 환경변수로 주입한다
 */
@ConfigurationProperties(prefix = "nextchapter.admin.bootstrap")
public record AdminBootstrapProperties(String email, String name, String password) {

    public AdminBootstrapProperties {
        if (name == null || name.isBlank()) {
            name = "관리자";
        }
    }

    public boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
