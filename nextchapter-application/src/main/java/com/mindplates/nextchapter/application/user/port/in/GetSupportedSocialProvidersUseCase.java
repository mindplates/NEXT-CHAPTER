package com.mindplates.nextchapter.application.user.port.in;

import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import java.util.Set;

/**
 * 실제로 로그인할 수 있는 제공자.
 *
 * <p>클라이언트가 로그인 버튼을 하드코딩하지 않게 하려는 것이다. 하드코딩하면 설정되지 않은
 * 제공자의 버튼이 화면에 남고, 사용자는 누른 뒤에야 실패를 본다.
 */
public interface GetSupportedSocialProvidersUseCase {

    Set<SocialProvider> supported();
}
