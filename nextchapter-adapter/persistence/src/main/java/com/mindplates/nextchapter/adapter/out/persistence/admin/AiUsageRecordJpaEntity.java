package com.mindplates.nextchapter.adapter.out.persistence.admin;

import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 사용량은 <b>추가만 된다</b> — 수정도 삭제도 없다. 그래서 {@code AuditTimestamps} 를 쓰지 않고
 * {@code created_at} 만 둔다. {@code updated_at} 이 있으면 "이 기록이 나중에 바뀔 수 있다"는 잘못된
 * 신호가 되고, 청구된 사용량이 바뀔 수 있다는 뜻이 되어 예산 판정의 근거가 흔들린다.
 */
@Entity
@Table(name = "ai_usage_records")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiUsageRecordJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** null 은 뼈대에 귀속되지 않는 호출이다. FK 를 걸지 않는다 — 뼈대가 지워져도 청구된 사용량은 남아야 한다. */
    @Column(name = "skeleton_id")
    private Long skeletonId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60, updatable = false)
    private AiStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private AiVendor vendor;

    @Column(nullable = false, length = 120, updatable = false)
    private String model;

    @Column(name = "input_tokens", nullable = false, updatable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false, updatable = false)
    private int outputTokens;

    @Column(name = "usage_date", nullable = false, updatable = false)
    private LocalDate usageDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
