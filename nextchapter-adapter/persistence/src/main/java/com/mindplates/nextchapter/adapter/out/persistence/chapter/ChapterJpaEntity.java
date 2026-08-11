package com.mindplates.nextchapter.adapter.out.persistence.chapter;

import com.mindplates.nextchapter.adapter.out.persistence.support.AuditTimestamps;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "chapters")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChapterJpaEntity extends AuditTimestamps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skeleton_id", nullable = false, updatable = false)
    private Long skeletonId;

    @Column(name = "chapter_key", nullable = false, length = 120, updatable = false)
    private String chapterKey;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Column(name = "block_id_sequence", nullable = false)
    private int blockIdSequence;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
