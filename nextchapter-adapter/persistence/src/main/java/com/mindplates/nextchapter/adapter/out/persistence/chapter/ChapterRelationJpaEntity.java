package com.mindplates.nextchapter.adapter.out.persistence.chapter;

import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** 관계는 만들어지고 지워지기만 한다 — 방향이나 타입을 고치는 것은 다른 관계다. */
@Entity
@Table(name = "chapter_relations")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChapterRelationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skeleton_id", nullable = false, updatable = false)
    private Long skeletonId;

    @Column(name = "from_chapter_id", nullable = false, updatable = false)
    private Long fromChapterId;

    @Column(name = "to_chapter_id", nullable = false, updatable = false)
    private Long toChapterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 40, updatable = false)
    private ChapterRelationType relationType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
