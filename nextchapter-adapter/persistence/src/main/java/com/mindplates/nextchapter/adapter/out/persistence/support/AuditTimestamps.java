package com.mindplates.nextchapter.adapter.out.persistence.support;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;

/**
 * created_at · updated_at 를 채우는 공통 상위 타입.
 *
 * <p>값은 UTC 다 — JVM 기본 시간대를 UTC 로 고정했고 이건 머신 타임스탬프다. 사람이 인지하는
 * 시각(오늘·일일 예산 경계)이 필요한 계산은 {@code AppTime.KST} 로 변환해서 쓴다.
 */
@MappedSuperclass
public abstract class AuditTimestamps {

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
