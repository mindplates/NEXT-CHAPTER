package com.mindplates.nextchapter.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.admin.port.in.command.CreateAdminAccountCommand;
import com.mindplates.nextchapter.application.admin.port.out.LoadAdminAccountPort;
import com.mindplates.nextchapter.application.admin.port.out.PasswordHasherPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveAdminAccountPort;
import com.mindplates.nextchapter.common.exception.DuplicateException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.admin.model.AdminAccount;
import com.mindplates.nextchapter.domain.admin.model.AdminRole;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 계정 관리")
class AdminAccountServiceTest {

    @Mock
    LoadAdminAccountPort loadAdminAccountPort;

    @Mock
    SaveAdminAccountPort saveAdminAccountPort;

    @Mock
    PasswordHasherPort passwordHasherPort;

    @InjectMocks
    AdminAccountService service;

    @Test
    @DisplayName("짧은 비밀번호는 거절한다")
    void rejectsShortPassword() {
        assertThatThrownBy(() -> service.create(new CreateAdminAccountCommand("a@b.com", "관리자", "short", null)))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("12자");

        verify(saveAdminAccountPort, never()).save(any());
    }

    @Test
    @DisplayName("이미 있는 이메일은 거절한다")
    void rejectsDuplicateEmail() {
        when(loadAdminAccountPort.findByEmail("a@b.com")).thenReturn(Optional.of(account()));

        assertThatThrownBy(() ->
                        service.create(new CreateAdminAccountCommand("A@B.com", "관리자", "long-enough-password", null)))
                .isInstanceOf(DuplicateException.class);
    }

    @Test
    @DisplayName("역할을 주지 않으면 ADMIN 으로 만든다")
    void defaultsToAdminRole() {
        when(loadAdminAccountPort.findByEmail("a@b.com")).thenReturn(Optional.empty());
        when(passwordHasherPort.hash(any())).thenReturn("{bcrypt}hash");
        when(saveAdminAccountPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AdminAccount created =
                service.create(new CreateAdminAccountCommand("a@b.com", "관리자", "long-enough-password", null));

        assertThat(created.role()).isEqualTo(AdminRole.ADMIN);
        assertThat(created.passwordHash()).isEqualTo("{bcrypt}hash");
    }

    @Test
    @DisplayName("계정이 이미 있으면 최초 계정을 만들지 않는다 — 설정값이 상시 백도어가 되지 않게")
    void bootstrapSkipsWhenAccountsExist() {
        when(loadAdminAccountPort.count()).thenReturn(1L);

        assertThat(service.ensureBootstrapAdmin("a@b.com", "관리자", "long-enough-password"))
                .isFalse();

        verify(saveAdminAccountPort, never()).save(any());
    }

    @Test
    @DisplayName("계정이 하나도 없으면 최초 계정을 만든다")
    void bootstrapCreatesFirstAccount() {
        when(loadAdminAccountPort.count()).thenReturn(0L);
        when(passwordHasherPort.hash(any())).thenReturn("{bcrypt}hash");
        when(saveAdminAccountPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.ensureBootstrapAdmin("Admin@B.com", "관리자", "long-enough-password"))
                .isTrue();

        verify(saveAdminAccountPort).save(any());
    }

    @Test
    @DisplayName("최초 계정도 비밀번호 길이 규칙을 지킨다")
    void bootstrapEnforcesPasswordPolicy() {
        when(loadAdminAccountPort.count()).thenReturn(0L);

        assertThatThrownBy(() -> service.ensureBootstrapAdmin("a@b.com", "관리자", "short"))
                .isInstanceOf(InvalidOperationException.class);
    }

    private static AdminAccount account() {
        return new AdminAccount(1L, "a@b.com", "관리자", "{bcrypt}hash", AdminRole.ADMIN, true, null, null, null);
    }
}
