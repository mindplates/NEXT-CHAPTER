package com.mindplates.nextchapter.application.admin.view;

import java.time.Instant;

/** 로그인 결과. {@code expiresAt} 을 함께 주는 이유는 클라이언트가 갱신 시점을 알아야 하기 때문이다. */
public record AdminTokenView(String accessToken, String tokenType, Instant expiresAt) {}
