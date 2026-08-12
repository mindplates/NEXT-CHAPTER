package com.mindplates.nextchapter.adapter.out.persistence.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.adapter.out.persistence.AbstractPostgresIT;
import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import com.mindplates.nextchapter.domain.user.model.User;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 여기서 확인해야 하는 것은 <b>정체성 UNIQUE 가 DB 에 실제로 있는가</b>다. 애플리케이션 조회만 두면
 * 동시 로그인 두 건이 사용자를 두 개 만들고, 그 순간 그 사람의 학습 레이어가 반으로 갈린다 — 목으로는
 * 드러나지 않는다.
 */
@Import(UserPersistenceAdapter.class)
@DisplayName("사용자 어댑터 (실제 PostgreSQL)")
class UserPersistenceAdapterIT extends AbstractPostgresIT {

    @Autowired
    UserPersistenceAdapter users;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("최초 저장이 ID 와 가입 시각을 채운다")
    void savesNewUser() {
        User saved = users.save(User.of(SocialProvider.GOOGLE, "sub-1", "learner@example.com", "학습자1"));
        entityManager.flush();

        assertThat(saved.id()).isNotNull();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(users.findByProviderIdentity(SocialProvider.GOOGLE, "sub-1")).isPresent();
    }

    /** 이 UNIQUE 가 "같은 사람이 두 계정으로 갈라지지 않는다"를 지키는 실제 수단이다. */
    @Test
    @DisplayName("같은 (제공자, 제공자 식별자)는 두 줄이 될 수 없다")
    void rejectsDuplicateIdentity() {
        users.save(User.of(SocialProvider.KAKAO, "dup-1", null, "닉"));
        entityManager.flush();

        User duplicate = User.of(SocialProvider.KAKAO, "dup-1", null, "닉2");

        // IDENTITY 전략이라 INSERT 가 persist 시점에 바로 나간다 — 위반이 flush 가 아니라 여기서 터진다.
        assertThatThrownBy(() -> users.save(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 같은 식별자라도 제공자가 다르면 다른 사람이다 — Google 의 12345 와 Kakao 의 12345 는 무관하다. */
    @Test
    @DisplayName("제공자가 다르면 같은 식별자를 쓸 수 있다")
    void identityIsScopedToProvider() {
        users.save(User.of(SocialProvider.GOOGLE, "12345", null, "구글"));
        users.save(User.of(SocialProvider.KAKAO, "12345", null, "카카오"));
        entityManager.flush();

        assertThat(users.findByProviderIdentity(SocialProvider.GOOGLE, "12345"))
                .get()
                .extracting(User::displayName)
                .isEqualTo("구글");
        assertThat(users.findByProviderIdentity(SocialProvider.KAKAO, "12345"))
                .get()
                .extracting(User::displayName)
                .isEqualTo("카카오");
    }

    /**
     * 갱신을 merge 로 하면 빌더의 null {@code createdAt} 이 반환 객체에 복사돼 "가입일"이 빈칸으로
     * 나간다. 관리 엔티티를 읽어 고치는 것이 그 대비다.
     */
    @Test
    @DisplayName("프로필 갱신이 가입 시각을 지우지 않는다")
    void updateKeepsCreatedAt() {
        User saved = users.save(User.of(SocialProvider.GOOGLE, "sub-2", "old@example.com", "옛이름"));
        entityManager.flush();

        User updated = users.save(saved.withProfile("new@example.com", "새이름", LocalDateTime.of(2026, 8, 12, 9, 0)));
        entityManager.flush();
        entityManager.clear();

        assertThat(updated.createdAt()).isNotNull();
        assertThat(updated.email()).isEqualTo("new@example.com");
        assertThat(users.findById(saved.id())).get().satisfies(user -> {
            assertThat(user.displayName()).isEqualTo("새이름");
            assertThat(user.lastLoginAt()).isEqualTo(LocalDateTime.of(2026, 8, 12, 9, 0));
            assertThat(user.providerUserId()).isEqualTo("sub-2");
        });
    }

    /** 같은 이메일로 두 제공자를 쓰는 사람이 있다. 두 번째 로그인이 실패하면 그 사람은 들어올 수 없다. */
    @Test
    @DisplayName("같은 이메일이 두 제공자에 있어도 저장된다")
    void emailIsNotUnique() {
        users.save(User.of(SocialProvider.GOOGLE, "sub-3", "same@example.com", "구글"));
        users.save(User.of(SocialProvider.KAKAO, "kakao-3", "same@example.com", "카카오"));

        entityManager.flush();

        assertThat(users.findByProviderIdentity(SocialProvider.KAKAO, "kakao-3"))
                .isPresent();
    }

    @Test
    @DisplayName("사라진 사용자를 갱신하려 하면 실패한다")
    void rejectsUpdatingMissingUser() {
        User ghost = new User(999_999L, SocialProvider.GOOGLE, "ghost", null, "유령", null, null, null);

        assertThatThrownBy(() -> users.save(ghost)).isInstanceOf(IllegalStateException.class);
    }
}
