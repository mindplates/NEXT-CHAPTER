package com.mindplates.nextchapter.adapter.out.persistence.generation;

import com.mindplates.nextchapter.adapter.out.persistence.support.AuditTimestamps;
import com.mindplates.nextchapter.domain.generation.model.GenerationFailureStatus;
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
@Table(name = "generation_failures")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GenerationFailureJpaEntity extends AuditTimestamps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** null 은 페이로드를 읽지 못한 경우다. 그때도 큐에는 올린다. */
    @Column(name = "skeleton_id", updatable = false)
    private Long skeletonId;

    /** 본문·자산 단계만 채워진다. */
    @Column(name = "chapter_id", updatable = false)
    private Long chapterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private GenerationStage stage;

    @Column(nullable = false, length = 200, updatable = false)
    private String topic;

    @Column(name = "partition_number", nullable = false, updatable = false)
    private int partition;

    /** {@code offset} 은 여러 DB 에서 예약어라 컬럼 이름을 달리 뒀다. */
    @Column(name = "record_offset", nullable = false, updatable = false)
    private long offset;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String payload;

    @Column(name = "error_type", nullable = false, length = 200, updatable = false)
    private String errorType;

    @Column(name = "error_message", columnDefinition = "text", updatable = false)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private int attempts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GenerationFailureStatus status;

    @Column(name = "resolved_by", length = 200)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
