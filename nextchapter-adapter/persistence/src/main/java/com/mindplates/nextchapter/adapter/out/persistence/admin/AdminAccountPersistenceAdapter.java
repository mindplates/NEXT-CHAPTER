package com.mindplates.nextchapter.adapter.out.persistence.admin;

import com.mindplates.nextchapter.application.admin.port.out.LoadAdminAccountPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveAdminAccountPort;
import com.mindplates.nextchapter.domain.admin.model.AdminAccount;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminAccountPersistenceAdapter implements LoadAdminAccountPort, SaveAdminAccountPort {

    private final AdminAccountJpaRepository accountRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AdminAccount> findAll() {
        return accountRepository.findAllByOrderByEmailAsc().stream()
                .map(AdminAccountPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminAccount> findById(Long id) {
        return accountRepository.findById(id).map(AdminAccountPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminAccount> findByEmail(String email) {
        return accountRepository.findByEmail(email).map(AdminAccountPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return accountRepository.count();
    }

    @Override
    @Transactional
    public AdminAccount save(AdminAccount account) {
        return toDomain(accountRepository.save(AdminAccountJpaEntity.builder()
                .id(account.id())
                .email(account.email())
                .name(account.name())
                .passwordHash(account.passwordHash())
                .role(account.role())
                .enabled(account.enabled())
                .lastLoginAt(account.lastLoginAt())
                .build()));
    }

    private static AdminAccount toDomain(AdminAccountJpaEntity entity) {
        return new AdminAccount(
                entity.getId(),
                entity.getEmail(),
                entity.getName(),
                entity.getPasswordHash(),
                entity.getRole(),
                entity.isEnabled(),
                entity.getLastLoginAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
