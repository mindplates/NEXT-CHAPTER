package com.mindplates.nextchapter.adapter.out.persistence.user;

import com.mindplates.nextchapter.application.user.port.out.LoadUserPort;
import com.mindplates.nextchapter.application.user.port.out.SaveUserPort;
import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import com.mindplates.nextchapter.domain.user.model.User;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements LoadUserPort, SaveUserPort {

    private final UserJpaRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByProviderIdentity(SocialProvider provider, String providerUserId) {
        return userRepository
                .findByProviderAndProviderUserId(provider, providerUserId)
                .map(UserPersistenceAdapter::toDomain);
    }

    /**
     * 갱신은 <b>관리 엔티티를 읽어 고친다</b> — {@code merge} 로 덮지 않는다.
     *
     * <p>빌더로 만든 분리 객체를 merge 하면 채우지 않은 {@code createdAt} 이 null 로 복사되고, 그 값이
     * 그대로 반환 객체에 남는다. "가입일"이 빈 화면에 나가는 것이 그 결과다.
     */
    @Override
    @Transactional
    public User save(User user) {
        if (user.id() == null) {
            return toDomain(userRepository.save(UserJpaEntity.builder()
                    .provider(user.provider())
                    .providerUserId(user.providerUserId())
                    .email(user.email())
                    .displayName(user.displayName())
                    .lastLoginAt(user.lastLoginAt())
                    .build()));
        }
        UserJpaEntity entity = userRepository
                .findById(user.id())
                .orElseThrow(() -> new IllegalStateException("사용자가 사라졌습니다. id=" + user.id()));
        entity.setEmail(user.email());
        entity.setDisplayName(user.displayName());
        entity.setLastLoginAt(user.lastLoginAt());
        return toDomain(userRepository.save(entity));
    }

    private static User toDomain(UserJpaEntity entity) {
        return new User(
                entity.getId(),
                entity.getProvider(),
                entity.getProviderUserId(),
                entity.getEmail(),
                entity.getDisplayName(),
                entity.getLastLoginAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
