package com.mindplates.nextchapter.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.admin.port.in.command.AdminLoginCommand;
import com.mindplates.nextchapter.application.admin.port.out.AdminTokenIssuerPort;
import com.mindplates.nextchapter.application.admin.port.out.LoadAdminAccountPort;
import com.mindplates.nextchapter.application.admin.port.out.PasswordHasherPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveAdminAccountPort;
import com.mindplates.nextchapter.application.admin.view.AdminTokenView;
import com.mindplates.nextchapter.common.exception.AuthenticationFailedException;
import com.mindplates.nextchapter.domain.admin.model.AdminAccount;
import com.mindplates.nextchapter.domain.admin.model.AdminRole;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 로그인")
class AdminAuthServiceTest {

    private static final String EMAIL = "admin@example.com";

    @Mock
    LoadAdminAccountPort loadAdminAccountPort;

    @Mock
    SaveAdminAccountPort saveAdminAccountPort;

    @Mock
    PasswordHasherPort passwordHasherPort;

    @Mock
    AdminTokenIssuerPort adminTokenIssuerPort;

    @InjectMocks
    AdminAuthService service;

    @Test
    @DisplayName("성공하면 토큰을 발급하고 마지막 로그인 시각을 남긴다")
    void issuesTokenOnSuccess() {
        AdminAccount account = enabledAccount();
        when(loadAdminAccountPort.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(passwordHasherPort.matches("correct-password", "{bcrypt}hash")).thenReturn(true);
        AdminTokenView token = new AdminTokenView("jwt", "Bearer", Instant.now().plusSeconds(60));
        when(adminTokenIssuerPort.issue(account)).thenReturn(token);

        AdminTokenView result = service.login(new AdminLoginCommand(EMAIL, "correct-password"));

        assertThat(result).isEqualTo(token);
        ArgumentCaptor<AdminAccount> saved = ArgumentCaptor.forClass(AdminAccount.class);
        verify(saveAdminAccountPort).save(saved.capture());
        assertThat(saved.getValue().lastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("이메일 대문자는 소문자로 조회된다")
    void normalizesEmail() {
        when(loadAdminAccountPort.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new AdminLoginCommand("ADMIN@Example.com", "whatever")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("계정이 없을 때와 비밀번호가 틀렸을 때의 메시지가 같다 — 계정 열거를 막는다")
    void doesNotLeakWhichPartFailed() {
        when(loadAdminAccountPort.findByEmail(EMAIL)).thenReturn(Optional.empty());
        String missingAccountMessage = messageOf(() -> service.login(new AdminLoginCommand(EMAIL, "any-password")));

        when(loadAdminAccountPort.findByEmail(EMAIL)).thenReturn(Optional.of(enabledAccount()));
        when(passwordHasherPort.matches(any(), any())).thenReturn(false);
        String wrongPasswordMessage = messageOf(() -> service.login(new AdminLoginCommand(EMAIL, "wrong-password")));

        assertThat(missingAccountMessage).isEqualTo(wrongPasswordMessage);
    }

    @Test
    @DisplayName("비활성 계정은 비밀번호가 맞아도 거절한다")
    void rejectsDisabledAccount() {
        when(loadAdminAccountPort.findByEmail(EMAIL))
                .thenReturn(Optional.of(enabledAccount().withEnabled(false)));
        when(passwordHasherPort.matches(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.login(new AdminLoginCommand(EMAIL, "correct-password")))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(adminTokenIssuerPort, never()).issue(any());
        verify(saveAdminAccountPort, never()).save(any());
    }

    private static AdminAccount enabledAccount() {
        return new AdminAccount(1L, EMAIL, "관리자", "{bcrypt}hash", AdminRole.ADMIN, true, null, null, null);
    }

    private static String messageOf(Runnable action) {
        try {
            action.run();
            throw new AssertionError("예외가 발생하지 않았다");
        } catch (AuthenticationFailedException e) {
            return e.getMessage();
        }
    }
}
