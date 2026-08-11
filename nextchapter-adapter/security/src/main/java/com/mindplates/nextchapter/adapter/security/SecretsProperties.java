package com.mindplates.nextchapter.adapter.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 저장 암호화용 마스터 키.
 *
 * <p>이 값은 DB 에 들어가지 않는다 — 암호문과 키가 같은 곳에 있으면 암호화가 의미를 잃는다.
 * 그래서 AI 프로바이더 키와 달리 이것만은 환경변수로 온다.
 *
 * @param secretKey 임의 길이 문자열. SHA-256 으로 256비트 키로 접힌다
 */
@ConfigurationProperties(prefix = "nextchapter.security")
public record SecretsProperties(String secretKey) {}
