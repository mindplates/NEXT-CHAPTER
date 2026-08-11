package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import com.mindplates.nextchapter.domain.catalog.model.TopicInputResolution;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicInputLogJpaRepository extends JpaRepository<TopicInputLogJpaEntity, Long> {

    /**
     * {@code :resolutions IS NULL} 로 조건을 끄는 형태를 쓴다. 파생 쿼리 메서드로 만들면 "상태 필터
     * 있음/없음 × 미검토 여부"의 조합만큼 메서드가 늘어난다.
     */
    @Query(
            """
            SELECT l FROM TopicInputLogJpaEntity l
             WHERE (:resolutions IS NULL OR l.resolution IN :resolutions)
            """)
    Page<TopicInputLogJpaEntity> findAny(
            @Param("resolutions") Collection<TopicInputResolution> resolutions, Pageable pageable);

    @Query(
            """
            SELECT l FROM TopicInputLogJpaEntity l
             WHERE l.reviewedAt IS NULL
               AND (:resolutions IS NULL OR l.resolution IN :resolutions)
            """)
    Page<TopicInputLogJpaEntity> findUnreviewed(
            @Param("resolutions") Collection<TopicInputResolution> resolutions, Pageable pageable);

    long countByNormalized(String normalized);
}
