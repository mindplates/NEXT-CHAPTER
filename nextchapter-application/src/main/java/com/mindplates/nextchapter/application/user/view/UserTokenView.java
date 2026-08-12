package com.mindplates.nextchapter.application.user.view;

import java.time.Instant;

/**
 * 로그인 응답. 프로필을 함께 실어 로그인 직후의 추가 왕복을 없앤다.
 *
 * <p>리프레시 토큰이 없다. 있으면 그것을 어디 둘지가 문제가 되고, 쿠키에 두는 순간 브라우저가 자동
 * 전송하는 자격 증명이 생겨 CSRF 방어가 필요해진다 — 무상태 Bearer 로 두기로 한 결정과 정면으로
 * 부딪힌다. 만료되면 다시 로그인한다.
 */
public record UserTokenView(String accessToken, String tokenType, Instant expiresAt, UserView user) {}
