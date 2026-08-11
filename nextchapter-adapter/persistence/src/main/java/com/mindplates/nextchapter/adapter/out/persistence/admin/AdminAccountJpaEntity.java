package com.mindplates.nextchapter.adapter.out.persistence.admin;

import com.mindplates.nextchapter.adapter.out.persistence.support.AuditTimestamps;
import com.mindplates.nextchapter.domain.admin.model.AdminRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "admin_accounts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AdminAccountJpaEntity extends AuditTimestamps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200, updatable = false)
    private String email;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AdminRole role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
