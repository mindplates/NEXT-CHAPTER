package com.mindplates.nextchapter.adapter.out.persistence.admin;

import com.mindplates.nextchapter.adapter.out.persistence.support.AuditTimestamps;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 단일 행 설정. {@code id} 를 애플리케이션이 {@link #SINGLETON_ID} 로 박고 {@code @GeneratedValue} 를 쓰지
 * 않는다 — 시퀀스를 쓰면 저장할 때마다 새 행이 생기고, DB {@code CHECK (id = 1)} 이 그걸 거절해 설정 변경이
 * 실패한다. 애초에 여러 행이 있을 수 없는 테이블이므로 키를 생성할 이유가 없다.
 */
@Entity
@Table(name = "ai_budget_settings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiBudgetSettingsJpaEntity extends AuditTimestamps {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    /** null 은 무제한이다. */
    @Column(name = "per_skeleton_token_limit")
    private Long perSkeletonTokenLimit;

    /** null 은 무제한이다. */
    @Column(name = "daily_token_limit")
    private Long dailyTokenLimit;

    @Column(name = "updated_by", length = 200)
    private String updatedBy;
}
