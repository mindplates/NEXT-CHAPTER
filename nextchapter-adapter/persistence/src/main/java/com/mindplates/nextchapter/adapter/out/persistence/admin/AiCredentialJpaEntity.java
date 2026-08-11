package com.mindplates.nextchapter.adapter.out.persistence.admin;

import com.mindplates.nextchapter.adapter.out.persistence.support.AuditTimestamps;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code api_key_cipher} 에는 암호문만 들어간다. 평문 컬럼은 존재하지 않는다. */
@Entity
@Table(name = "ai_credentials")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiCredentialJpaEntity extends AuditTimestamps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private AiVendor vendor;

    @Column(name = "api_key_cipher", nullable = false, columnDefinition = "TEXT")
    private String apiKeyCipher;

    @Column(name = "key_hint", nullable = false, length = 40)
    private String keyHint;
}
