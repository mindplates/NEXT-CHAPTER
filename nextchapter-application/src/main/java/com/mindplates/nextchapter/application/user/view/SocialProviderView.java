package com.mindplates.nextchapter.application.user.view;

import com.mindplates.nextchapter.domain.user.model.SocialProvider;

/**
 * 로그인 화면이 버튼 하나를 그리는 데 필요한 것 전부.
 *
 * <p>동의 화면 주소를 서버가 조립해 내려 주므로 클라이언트에 {@code client_id} 가 없다. 제공자를 추가하는
 * 비용이 <b>설정 한 벌</b>로 끝나고, "설정은 됐는데 버튼이 없다"거나 그 반대인 상태가 생기지 않는다.
 */
public record SocialProviderView(SocialProvider provider, String authorizationUri) {}
