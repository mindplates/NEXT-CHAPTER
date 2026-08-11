package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import com.mindplates.nextchapter.adapter.out.persistence.support.AuditTimestamps;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputResolution;
import com.mindplates.nextchapter.domain.catalog.model.TopicMatchSource;
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

/**
 * 후보 ID 목록을 정규화하지 않고 쉼표 문자열로 둔다.
 *
 * <p>이 값은 "그때 무엇을 보여 줬는가"의 기록이고 조인·집계 대상이 아니다. 별도 테이블로 쪼개면 행이
 * 입력 수 × 후보 수만큼 늘어나는데, 얻는 것은 없다. 유일한 사용처가 확인 요청의 후보 검증이다.
 */
@Entity
@Table(name = "topic_input_logs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TopicInputLogJpaEntity extends AuditTimestamps {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raw_input", nullable = false, length = 500, updatable = false)
    private String rawInput;

    @Column(length = 500, updatable = false)
    private String normalized;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TopicInputResolution resolution;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_source", length = 40)
    private TopicMatchSource matchSource;

    @Column(name = "matched_topic_id")
    private Long matchedTopicId;

    @Column(name = "proposed_field_id")
    private Long proposedFieldId;

    @Column(name = "proposed_topic_name", length = 160)
    private String proposedTopicName;

    @Column(name = "proposed_topic_slug", length = 120)
    private String proposedTopicSlug;

    @Column(name = "candidate_topic_ids", length = 200)
    private String candidateTopicIds;

    @Column(name = "user_id", length = 200)
    private String userId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by", length = 200)
    private String reviewedBy;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;
}
