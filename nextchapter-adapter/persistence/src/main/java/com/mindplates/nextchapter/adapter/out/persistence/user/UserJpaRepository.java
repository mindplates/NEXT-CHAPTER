package com.mindplates.nextchapter.adapter.out.persistence.user;

import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

    Optional<UserJpaEntity> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
}
