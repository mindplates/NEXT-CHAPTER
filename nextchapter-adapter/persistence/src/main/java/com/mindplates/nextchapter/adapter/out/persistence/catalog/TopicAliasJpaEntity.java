package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 별칭은 만들어지고 지워지기만 한다 — 표현을 고치는 것은 다른 별칭이므로 updated_at 이 없다.
 *
 * <p>{@code normalized} 는 도메인에서 파생값이지만 컬럼으로도 저장한다. 유일 제약과 인덱스 조회가
 * 저장된 값을 필요로 하기 때문이다. 읽을 때는 도메인이 다시 계산하므로, 정규화 규칙이 바뀌면
 * 재계산 배치로 이 컬럼을 갱신하면 된다.
 */
@Entity
@Table(name = "catalog_topic_aliases")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TopicAliasJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(nullable = false, length = 200)
    private String alias;

    @Column(nullable = false, length = 200)
    private String normalized;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
