package com.mindplates.nextchapter.adapter.out.persistence.skeleton;

import com.mindplates.nextchapter.adapter.out.persistence.support.AuditTimestamps;
import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;
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

/**
 * {@code topic_id} 의 UNIQUE 제약이 "뼈대는 최말단 주제에 1:1" 을 지키는 실제 수단이다.
 * 애플리케이션 검사만 두면 동시 요청 두 건이 같은 주제로 뼈대를 두 개 만들 수 있고, 그 순간
 * 집단 신호가 반으로 갈린다.
 *
 * <p>상태를 {@code ORDINAL} 이 아니라 문자열로 저장하는 이유는 enum 에 값이 끼어들면 기존
 * 행의 의미가 조용히 바뀌기 때문이다.
 */
@Entity
@Table(name = "skeletons")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SkeletonJpaEntity extends AuditTimestamps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic_id", nullable = false, updatable = false)
    private Long topicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SkeletonStatus status;
}
