package com.mindplates.nextchapter.adapter.out.persistence.generation;

import com.mindplates.nextchapter.adapter.out.persistence.support.AuditTimestamps;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import com.mindplates.nextchapter.domain.generation.model.AiBatchStatus;
import com.mindplates.nextchapter.domain.generation.model.GenerationStage;
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
@Table(name = "ai_batch_jobs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiBatchJobJpaEntity extends AuditTimestamps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skeleton_id", nullable = false, updatable = false)
    private Long skeletonId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private GenerationStage stage;

    /**
     * 제출한 벤더와 모델은 <b>바뀌지 않는다</b>({@code updatable = false}). 배치는 최대 24시간 살아 있고 그
     * 사이 관리자가 단계 설정을 바꿀 수 있는데, 이 값이 따라 바뀌면 조회가 다른 벤더에게 간다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private AiVendor vendor;

    @Column(nullable = false, length = 120, updatable = false)
    private String model;

    @Column(name = "batch_id", nullable = false, length = 200, updatable = false)
    private String batchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiBatchStatus status;

    @Column(name = "item_count", nullable = false, updatable = false)
    private int itemCount;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
