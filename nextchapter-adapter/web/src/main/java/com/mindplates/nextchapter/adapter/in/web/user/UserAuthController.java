package com.mindplates.nextchapter.adapter.in.web.user;

import com.mindplates.nextchapter.adapter.in.web.support.AuthenticatedUser;
import com.mindplates.nextchapter.adapter.in.web.user.dto.SocialLoginRequest;
import com.mindplates.nextchapter.application.user.port.in.GetSupportedSocialProvidersUseCase;
import com.mindplates.nextchapter.application.user.port.in.GetUserUseCase;
import com.mindplates.nextchapter.application.user.port.in.SocialLoginUseCase;
import com.mindplates.nextchapter.application.user.view.SocialProviderView;
import com.mindplates.nextchapter.application.user.view.UserTokenView;
import com.mindplates.nextchapter.application.user.view.UserView;
import com.mindplates.nextchapter.common.response.ApiResponse;
import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 인증. 관리자 경로({@code /api/admin/auth})와 완전히 분리돼 있다.
 *
 * <p>인가 코드를 <b>서버가</b> 교환한다. 클라이언트가 제공자 토큰을 직접 받아 넘기면 서버는 그 토큰이
 * 우리 앱을 위해 발급된 것인지 확인할 수 없고, 클라이언트마다 제공자 SDK 와 시크릿 취급 규칙이 생긴다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final SocialLoginUseCase socialLoginUseCase;
    private final GetUserUseCase getUserUseCase;
    private final GetSupportedSocialProvidersUseCase getSupportedSocialProvidersUseCase;

    /**
     * 로그인 화면이 버튼을 그리기 위해 인증 전에 읽는다. 하드코딩하면 설정 안 된 제공자의 버튼이 남는다.
     *
     * <p>동의 화면 주소를 서버가 조립해 내려 준다 — 클라이언트에 {@code client_id} 를 박지 않기 위해서다.
     * {@code redirectUri} 를 클라이언트가 정하는 이유는 웹과 앱의 콜백 주소가 다르기 때문이고, 인가 코드를
     * 교환할 때 <b>같은 값</b>을 다시 보내야 한다.
     */
    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<List<SocialProviderView>>> providers(
            @RequestParam String redirectUri, @RequestParam(required = false) String state) {
        return ResponseEntity.ok(ApiResponse.ok(getSupportedSocialProvidersUseCase.available(redirectUri, state)));
    }

    @PostMapping("/social/{provider}")
    public ResponseEntity<ApiResponse<UserTokenView>> login(
            @PathVariable SocialProvider provider, @RequestBody @Valid SocialLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(socialLoginUseCase.login(request.toCommand(provider))));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserView>> me(Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(getUserUseCase.getById(AuthenticatedUser.requireId(principal))));
    }
}
