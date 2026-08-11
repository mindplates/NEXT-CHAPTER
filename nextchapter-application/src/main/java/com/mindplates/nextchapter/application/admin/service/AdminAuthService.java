package com.mindplates.nextchapter.application.admin.service;

import com.mindplates.nextchapter.application.admin.port.in.AdminLoginUseCase;
import com.mindplates.nextchapter.application.admin.port.in.command.AdminLoginCommand;
import com.mindplates.nextchapter.application.admin.port.out.AdminTokenIssuerPort;
import com.mindplates.nextchapter.application.admin.port.out.LoadAdminAccountPort;
import com.mindplates.nextchapter.application.admin.port.out.PasswordHasherPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveAdminAccountPort;
import com.mindplates.nextchapter.application.admin.view.AdminTokenView;
import com.mindplates.nextchapter.common.exception.AuthenticationFailedException;
import com.mindplates.nextchapter.common.text.Strings;
import com.mindplates.nextchapter.domain.admin.model.AdminAccount;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminAuthService implements AdminLoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    /**
     * 실패 사유를 구분하지 않는다. "그런 계정이 없다"와 "비밀번호가 틀렸다"를 나누면 응답만으로
     * 어떤 이메일이 관리자인지 열거할 수 있다. 사유는 로그에만 남는다.
     */
    private static final String LOGIN_FAILED = "이메일 또는 비밀번호가 올바르지 않습니다.";

    private final LoadAdminAccountPort loadAdminAccountPort;
    private final SaveAdminAccountPort saveAdminAccountPort;
    private final PasswordHasherPort passwordHasherPort;
    private final AdminTokenIssuerPort adminTokenIssuerPort;

    @Override
    public AdminTokenView login(AdminLoginCommand command) {
        String email = Strings.requireText(command.email(), "이메일").toLowerCase(Locale.ROOT);
        String password = Strings.requireText(command.password(), "비밀번호");

        Optional<AdminAccount> found = loadAdminAccountPort.findByEmail(email);
        if (found.isEmpty()) {
            log.warn("[관리자 로그인] 없는 계정 email={}", email);
            throw new AuthenticationFailedException(LOGIN_FAILED);
        }

        AdminAccount account = found.get();
        if (!passwordHasherPort.matches(password, account.passwordHash())) {
            log.warn("[관리자 로그인] 비밀번호 불일치 email={}", email);
            throw new AuthenticationFailedException(LOGIN_FAILED);
        }
        if (!account.enabled()) {
            log.warn("[관리자 로그인] 비활성 계정 email={}", email);
            throw new AuthenticationFailedException(LOGIN_FAILED);
        }

        saveAdminAccountPort.save(account.withLastLoginAt(LocalDateTime.now()));
        return adminTokenIssuerPort.issue(account);
    }
}
