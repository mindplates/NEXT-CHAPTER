package com.mindplates.nextchapter.application.user.port.out;

import com.mindplates.nextchapter.domain.user.model.SocialProvider;

/**
 * 인가 코드를 프로필로 바꾼다 — 제공자 하나당 구현 하나.
 *
 * <p>코드 교환을 <b>서버가</b> 하는 이유는 클라이언트가 웹·모바일·태블릿으로 늘어나기 때문이다.
 * 각 클라이언트가 직접 제공자 토큰을 받아 서버에 넘기면 클라이언트마다 제공자 SDK와 시크릿 취급
 * 규칙이 생기고, 서버는 그 토큰이 우리 앱을 위해 발급된 것인지 확인할 방법이 없다.
 *
 * <p>{@link #provider()} 를 갖는 이유는 벤더 레지스트리와 같다 — <b>구현 존재 여부가 곧 지원 여부
 * 조회</b>다. 제공자 목록을 따로 하드코딩하면 설정과 구현이 어긋난 상태가 만들어진다.
 */
public interface SocialProfileClientPort {

    SocialProvider provider();

    /**
     * @param redirectUri 인가 코드를 받을 때 쓴 값과 <b>같아야</b> 한다. 제공자가 이 값을 대조하며,
     *     어긋나면 코드 교환이 실패한다. 그래서 서버가 상수로 갖지 않고 요청에서 받는다 — 웹과 앱의
     *     리다이렉트 주소가 다르다
     */
    SocialProfile exchange(String authorizationCode, String redirectUri);
}
