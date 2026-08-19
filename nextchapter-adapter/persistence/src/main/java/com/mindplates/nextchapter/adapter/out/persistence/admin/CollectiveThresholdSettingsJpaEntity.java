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
 * 실패한다.
 */
@Entity
@Table(name = "collective_threshold_settings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CollectiveThresholdSettingsJpaEntity extends AuditTimestamps {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(name = "question_threshold", nullable = false)
    private int questionThreshold;

    @Column(name = "min_attempts", nullable = false)
    private int minAttempts;

    @Column(name = "wrong_rate_percent", nullable = false)
    private int wrongRatePercent;

    @Column(name = "updated_by", length = 200)
    private String updatedBy;
}
