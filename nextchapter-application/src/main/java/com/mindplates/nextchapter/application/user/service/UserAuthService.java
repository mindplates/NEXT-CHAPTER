package com.mindplates.nextchapter.application.user.service;

import com.mindplates.nextchapter.application.user.port.in.GetUserUseCase;
import com.mindplates.nextchapter.application.user.port.in.SocialLoginUseCase;
import com.mindplates.nextchapter.application.user.port.in.command.SocialLoginCommand;
import com.mindplates.nextchapter.application.user.port.out.LoadUserPort;
import com.mindplates.nextchapter.application.user.port.out.SaveUserPort;
import com.mindplates.nextchapter.application.user.port.out.SocialProfile;
import com.mindplates.nextchapter.application.user.port.out.UserTokenIssuerPort;
import com.mindplates.nextchapter.application.user.view.UserTokenView;
import com.mindplates.nextchapter.application.user.view.UserView;
import com.mindplates.nextchapter.common.exception.AuthenticationFailedException;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.text.Strings;
import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import com.mindplates.nextchapter.domain.user.model.User;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인. 인가 코드를 프로필로 바꾸고, 없으면 만들고, 자체 JWT 를 발급한다.
 *
 * <p>최초 로그인이 곧 가입이다. 별도 가입 절차를 두지 않는 이유는 공개 서비스라 진입장벽을 낮춰야
 * 하고, 제공자가 이미 확인한 정체성 위에 다시 폼을 세울 이유가 없기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserAuthService implements SocialLoginUseCase, GetUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserAuthService.class);

    private final SocialProviderRegistry socialProviderRegistry;
    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final UserTokenIssuerPort userTokenIssuerPort;

    @Override
    public UserTokenView login(SocialLoginCommand command) {
        SocialProvider provider = requireProvider(command.provider());
        String code = Strings.requireText(command.authorizationCode(), "인가 코드");
        String redirectUri = Strings.requireText(command.redirectUri(), "리다이렉트 URI");

        SocialProfile profile = socialProviderRegistry.require(provider).exchange(code, redirectUri);
        if (profile.provider() != provider) {
            // 어댑터가 다른 제공자의 프로필을 돌려주면 그 사용자는 남의 계정에 붙는다. 조용히 넘기지 않는다.
            throw new IllegalStateException("제공자 불일치: 요청=" + provider + " 응답=" + profile.provider());
        }

        User user = upsert(profile);
        log.info("[사용자 로그인] provider={} userId={}", provider, user.id());
        return userTokenIssuerPort.issue(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserView getById(Long id) {
        return UserView.from(loadUserPort.findById(id).orElseThrow(() -> new EntityNotFoundException("사용자", id)));
    }

    /**
     * 있으면 프로필을 갱신하고, 없으면 만든다.
     *
     * <p>정체성 조회를 {@code (제공자, 제공자 식별자)} 로 하는 것이 요점이다. 이메일로 찾으면 제공자
     * 쪽에서 이메일을 바꾼 사용자가 새 계정을 받고, 그 순간 그 사람의 학습 레이어가 끊긴다.
     */
    private User upsert(SocialProfile profile) {
        LocalDateTime now = LocalDateTime.now();
        return loadUserPort
                .findByProviderIdentity(profile.provider(), profile.providerUserId())
                .map(existing -> saveUserPort.save(existing.withProfile(profile.email(), profile.displayName(), now)))
                .orElseGet(() -> saveUserPort.save(
                        User.of(profile.provider(), profile.providerUserId(), profile.email(), profile.displayName())
                                .withProfile(profile.email(), profile.displayName(), now)));
    }

    private static SocialProvider requireProvider(SocialProvider provider) {
        if (provider == null) {
            throw new AuthenticationFailedException("소셜 제공자를 알 수 없습니다.");
        }
        return provider;
    }
}
