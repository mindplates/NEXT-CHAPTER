package com.mindplates.nextchapter.application.admin.service;

import com.mindplates.nextchapter.application.admin.port.in.CreateAdminAccountUseCase;
import com.mindplates.nextchapter.application.admin.port.in.EnsureBootstrapAdminUseCase;
import com.mindplates.nextchapter.application.admin.port.in.GetAdminAccountUseCase;
import com.mindplates.nextchapter.application.admin.port.in.command.CreateAdminAccountCommand;
import com.mindplates.nextchapter.application.admin.port.out.LoadAdminAccountPort;
import com.mindplates.nextchapter.application.admin.port.out.PasswordHasherPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveAdminAccountPort;
import com.mindplates.nextchapter.common.exception.DuplicateException;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.common.text.Strings;
import com.mindplates.nextchapter.domain.admin.model.AdminAccount;
import com.mindplates.nextchapter.domain.admin.model.AdminRole;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminAccountService
        implements GetAdminAccountUseCase, CreateAdminAccountUseCase, EnsureBootstrapAdminUseCase {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountService.class);

    /** 아래로 내리면 사전 공격에 노출되는 계정이 곧 운영 전체 권한이다. */
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final LoadAdminAccountPort loadAdminAccountPort;
    private final SaveAdminAccountPort saveAdminAccountPort;
    private final PasswordHasherPort passwordHasherPort;

    @Override
    @Transactional(readOnly = true)
    public List<AdminAccount> list() {
        return loadAdminAccountPort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminAccount getByEmail(String email) {
        return loadAdminAccountPort
                .findByEmail(email.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new EntityNotFoundException("관리자 계정", email));
    }

    @Override
    public AdminAccount create(CreateAdminAccountCommand command) {
        String email = Strings.requireText(command.email(), "이메일").toLowerCase(Locale.ROOT);
        requireStrongPassword(command.password());
        loadAdminAccountPort.findByEmail(email).ifPresent(existing -> {
            throw new DuplicateException("관리자 계정", "email", email);
        });
        AdminRole role = command.role() == null ? AdminRole.ADMIN : command.role();
        return saveAdminAccountPort.save(
                AdminAccount.create(email, command.name(), passwordHasherPort.hash(command.password()), role));
    }

    /**
     * 계정이 이미 하나라도 있으면 아무것도 하지 않는다. "없을 때만"이라는 조건이 이 경로를 부트스트랩
     * 수단으로 묶어 두는 유일한 장치다 — 조건이 없으면 설정 파일에 적힌 비밀번호가 상시 백도어가 된다.
     */
    @Override
    public boolean ensureBootstrapAdmin(String email, String name, String rawPassword) {
        if (loadAdminAccountPort.count() > 0) {
            return false;
        }
        requireStrongPassword(rawPassword);
        AdminAccount created = saveAdminAccountPort.save(AdminAccount.create(
                email.toLowerCase(Locale.ROOT), name, passwordHasherPort.hash(rawPassword), AdminRole.ADMIN));
        log.info("[관리자] 최초 관리자 계정을 생성했다 email={}", created.email());
        return true;
    }

    private static void requireStrongPassword(String rawPassword) {
        String password = Strings.requireText(rawPassword, "비밀번호");
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidOperationException("비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
        }
    }
}
