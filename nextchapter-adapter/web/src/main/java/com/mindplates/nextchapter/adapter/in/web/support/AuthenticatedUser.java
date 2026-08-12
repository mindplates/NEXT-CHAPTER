package com.mindplates.nextchapter.adapter.in.web.support;

import com.mindplates.nextchapter.common.exception.AuthenticationFailedException;
import java.security.Principal;

/**
 * 토큰 주체에서 사용자 ID 를 꺼낸다.
 *
 * <p>{@link Principal} 로 받는 이유는 web 어댑터가 security 어댑터에 의존할 수 없기 때문이다 —
 * 어댑터끼리의 의존은 빌드가 막는다. 사용자 토큰의 subject 가 사용자 ID 라서 이것으로 충분하다.
 *
 * <p>파싱 실패를 401 로 매핑하는 것이 요점이다. 그 상태는 "관리자 토큰이 사용자 API 에 들어왔다"이고,
 * 500 으로 새면 인가 문제가 서버 오류로 위장된다. 필터 체인이 역할로 막고 있으므로 여기 오는 것은
 * 이미 비정상이지만, 막는 쪽이 두 겹인 것이 맞다.
 */
public final class AuthenticatedUser {

    private AuthenticatedUser() {}

    public static Long requireId(Principal principal) {
        if (principal == null) {
            throw new AuthenticationFailedException("로그인이 필요합니다.");
        }
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException e) {
            throw new AuthenticationFailedException("사용자 토큰이 아닙니다.");
        }
    }
}
