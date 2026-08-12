package com.mindplates.nextchapter.domain.user.model;

/**
 * 지원하는 소셜 로그인 제공자.
 *
 * <p>enum 인 이유는 제공자마다 <b>어댑터가 필요하기 때문</b>이다. 토큰 교환 응답과 프로필 응답의
 * 필드 이름이 제공자마다 달라 설정값만으로는 붙지 않는다. 문자열로 두면 설정에 오타를 넣어도
 * 저장이 되고, 실패는 사용자가 로그인 버튼을 누른 순간에야 나타난다 — AI 벤더를 저장 시점에
 * 거절하기로 한 것과 같은 이유다.
 */
public enum SocialProvider {
    GOOGLE,
    KAKAO
}
