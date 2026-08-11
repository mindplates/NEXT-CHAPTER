package com.mindplates.nextchapter.adapter.out.persistence.chapter;

import com.mindplates.nextchapter.domain.chapter.model.ChapterVersionSource;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 버전은 만들어지고 읽히기만 한다 — {@code updated_at} 이 없는 이유다. 스냅샷을 고치면 그건 새
 * 버전이지 같은 버전의 수정이 아니고, 이력이 사후에 바뀔 수 있으면 감사 추적이 성립하지 않는다.
 *
 * <p>{@code body} 는 {@code jsonb} 다. 문자열로 들고 Hibernate 에는 JSON 이라고만 알려 준다 — 블록
 * 문서의 구조를 ORM 이 알 필요가 없고, 알게 하면 블록 타입이 늘 때마다 매핑이 따라 움직인다.
 */
@Entity
@Table(name = "chapter_versions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChapterVersionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chapter_id", nullable = false, updatable = false)
    private Long chapterId;

    @Column(nullable = false, updatable = false)
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String body;

    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ChapterVersionSource source;

    @Column(name = "created_by", length = 200)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
