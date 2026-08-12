package com.mindplates.nextchapter.application.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.user.port.in.command.SocialLoginCommand;
import com.mindplates.nextchapter.application.user.port.out.LoadUserPort;
import com.mindplates.nextchapter.application.user.port.out.SaveUserPort;
import com.mindplates.nextchapter.application.user.port.out.SocialProfile;
import com.mindplates.nextchapter.application.user.port.out.SocialProfileClientPort;
import com.mindplates.nextchapter.application.user.port.out.UserTokenIssuerPort;
import com.mindplates.nextchapter.application.user.view.UserTokenView;
import com.mindplates.nextchapter.application.user.view.UserView;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import com.mindplates.nextchapter.domain.user.model.User;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 여기서 틀리면 <b>같은 사람이 두 계정으로 갈라진다.</b> 그 상태는 에러 없이 일어나고, 그 사람의 학습
 * 레이어와 신호가 반으로 나뉜다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("소셜 로그인")
class UserAuthServiceTest {

    @Mock
    SocialProfileClientPort googleClient;

    @Mock
    LoadUserPort loadUserPort;

    @Mock
    SaveUserPort saveUserPort;

    @Mock
    UserTokenIssuerPort userTokenIssuerPort;

    UserAuthService service;

    @BeforeEach
    void setUp() {
        when(googleClient.provider()).thenReturn(SocialProvider.GOOGLE);
        service = new UserAuthService(
                new SocialProviderRegistry(List.of(googleClient)), loadUserPort, saveUserPort, userTokenIssuerPort);
    }

    private static SocialLoginCommand command() {
        return new SocialLoginCommand(SocialProvider.GOOGLE, "code-1", "http://localhost:34100/login/google");
    }

    private static SocialProfile profile() {
        return new SocialProfile(SocialProvider.GOOGLE, "sub-1", "learner@example.com", "학습자1");
    }

    private void stubIssuer() {
        when(userTokenIssuerPort.issue(any()))
                .thenAnswer(invocation ->
                        new UserTokenView("token", "Bearer", Instant.now(), UserView.from(invocation.getArgument(0))));
    }

    /** 정체성이 (제공자, 제공자 식별자)라는 것이 이 테스트의 대상이다. */
    @Test
    @DisplayName("최초 로그인이 가입이다")
    void firstLoginCreatesUser() {
        when(googleClient.exchange("code-1", "http://localhost:34100/login/google"))
                .thenReturn(profile());
        when(loadUserPort.findByProviderIdentity(SocialProvider.GOOGLE, "sub-1"))
                .thenReturn(Optional.empty());
        when(saveUserPort.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 9L));
        stubIssuer();

        UserTokenView token = service.login(command());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(saveUserPort).save(saved.capture());
        assertThat(saved.getValue().id()).isNull();
        assertThat(saved.getValue().providerUserId()).isEqualTo("sub-1");
        assertThat(saved.getValue().lastLoginAt()).isNotNull();
        assertThat(token.user().id()).isEqualTo(9L);
    }

    /** 두 번째 로그인이 새 사용자를 만들면 학습 레이어가 갈린다 — 이 제품에서 가장 조용한 실패다. */
    @Test
    @DisplayName("두 번째 로그인은 기존 사용자를 갱신한다")
    void repeatLoginUpdatesExisting() {
        when(googleClient.exchange("code-1", "http://localhost:34100/login/google"))
                .thenReturn(profile());
        User existing = new User(
                9L,
                SocialProvider.GOOGLE,
                "sub-1",
                "old@example.com",
                "옛이름",
                null,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                null);
        when(loadUserPort.findByProviderIdentity(SocialProvider.GOOGLE, "sub-1"))
                .thenReturn(Optional.of(existing));
        when(saveUserPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stubIssuer();

        service.login(command());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(saveUserPort).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(9L);
        assertThat(saved.getValue().email()).isEqualTo("learner@example.com");
        assertThat(saved.getValue().displayName()).isEqualTo("학습자1");
        assertThat(saved.getValue().createdAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    /** 어댑터가 없는 제공자로 폴백하면 Kakao 버튼을 눌렀는데 Google 로 로그인된다. */
    @Test
    @DisplayName("어댑터 없는 제공자는 거절한다 — 폴백하지 않는다")
    void rejectsUnsupportedProvider() {
        SocialLoginCommand kakao = new SocialLoginCommand(SocialProvider.KAKAO, "code-1", "http://localhost/cb");

        assertThatThrownBy(() -> service.login(kakao)).isInstanceOf(InvalidOperationException.class);
        verify(saveUserPort, never()).save(any());
    }

    /** 어댑터가 다른 제공자의 프로필을 돌려주면 그 사용자는 남의 계정에 붙는다. */
    @Test
    @DisplayName("응답 제공자가 요청과 다르면 거절한다")
    void rejectsProviderMismatch() {
        when(googleClient.exchange("code-1", "http://localhost:34100/login/google"))
                .thenReturn(new SocialProfile(SocialProvider.KAKAO, "sub-1", null, "이름"));
        SocialLoginCommand command = command();

        assertThatThrownBy(() -> service.login(command)).isInstanceOf(IllegalStateException.class);
        verify(saveUserPort, never()).save(any());
    }

    @Test
    @DisplayName("인가 코드가 비면 제공자를 부르지 않는다")
    void rejectsBlankCode() {
        SocialLoginCommand blank = new SocialLoginCommand(SocialProvider.GOOGLE, " ", "http://localhost/cb");

        assertThatThrownBy(() -> service.login(blank)).isInstanceOf(IllegalArgumentException.class);
        verify(googleClient, never()).exchange(any(), any());
    }

    @Test
    @DisplayName("리다이렉트 URI 가 비면 거절한다")
    void rejectsBlankRedirectUri() {
        SocialLoginCommand blank = new SocialLoginCommand(SocialProvider.GOOGLE, "code-1", " ");

        assertThatThrownBy(() -> service.login(blank)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("없는 사용자 조회는 404 다")
    void missingUserIsNotFound() {
        when(loadUserPort.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(404L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("프로필 조회는 제공자 식별자를 내보내지 않는다")
    void profileHidesProviderIdentity() {
        when(loadUserPort.findById(9L))
                .thenReturn(
                        Optional.of(new User(9L, SocialProvider.GOOGLE, "sub-1", "a@b.c", "학습자1", null, null, null)));

        UserView view = service.getById(9L);

        assertThat(view.id()).isEqualTo(9L);
        assertThat(view.displayName()).isEqualTo("학습자1");
    }

    private static User withId(User user, Long id) {
        return new User(
                id,
                user.provider(),
                user.providerUserId(),
                user.email(),
                user.displayName(),
                user.lastLoginAt(),
                LocalDateTime.now(),
                LocalDateTime.now());
    }
}
