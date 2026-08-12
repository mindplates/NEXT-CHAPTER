package com.mindplates.nextchapter.application.user.port.in;

import com.mindplates.nextchapter.application.user.view.SocialProviderView;
import java.util.List;

/**
 * 실제로 로그인할 수 있는 제공자와 그 동의 화면 주소.
 *
 * <p>클라이언트가 로그인 버튼을 하드코딩하지 않게 하려는 것이다. 하드코딩하면 설정되지 않은 제공자의
 * 버튼이 화면에 남고, 사용자는 누른 뒤에야 실패를 본다.
 */
public interface GetSupportedSocialProvidersUseCase {

    /**
     * @param redirectUri 콜백 주소. 인가 코드를 교환할 때 <b>같은 값</b>을 보내야 하므로 클라이언트가 정한다 —
     *     웹과 앱의 리다이렉트 주소가 다르다
     * @param state 클라이언트가 만든 임의 값. 콜백에서 대조해 다른 사이트가 시작한 흐름을 걸러 낸다
     */
    List<SocialProviderView> available(String redirectUri, String state);
}
