package com.mindplates.nextchapter.application.user.port.out;

import com.mindplates.nextchapter.domain.user.model.SocialProvider;

/**
 * 제공자에서 받아온 프로필. 어댑터가 제공자별 응답 필드를 여기로 맞춘다.
 *
 * @param providerUserId 제공자의 안정 식별자(Google 의 {@code sub}, Kakao 의 {@code id})
 * @param email 제공자가 주지 않을 수 있다. null 이 정상 값이다
 */
public record SocialProfile(SocialProvider provider, String providerUserId, String email, String displayName) {}
