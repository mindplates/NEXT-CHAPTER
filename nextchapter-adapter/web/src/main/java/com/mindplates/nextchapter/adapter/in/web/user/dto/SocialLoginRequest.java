package com.mindplates.nextchapter.adapter.in.web.user.dto;

import com.mindplates.nextchapter.application.user.port.in.command.SocialLoginCommand;
import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import jakarta.validation.constraints.NotBlank;

/**
 * @param redirectUri 인가 코드를 받을 때 쓴 값과 같아야 한다. 서버 상수로 두지 않는 이유는 웹과 앱의
 *     리다이렉트 주소가 다르기 때문이다
 */
public record SocialLoginRequest(@NotBlank String authorizationCode, @NotBlank String redirectUri) {

    public SocialLoginCommand toCommand(SocialProvider provider) {
        return new SocialLoginCommand(provider, authorizationCode, redirectUri);
    }
}
