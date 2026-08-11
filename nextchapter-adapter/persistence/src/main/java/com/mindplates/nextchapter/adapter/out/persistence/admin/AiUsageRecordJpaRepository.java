package com.mindplates.nextchapter.adapter.out.persistence.admin;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiUsageRecordJpaRepository extends JpaRepository<AiUsageRecordJpaEntity, Long> {

    /**
     * 합계는 DB 에서 낸다. 레코드를 다 읽어 자바에서 더하면 뼈대 하나가 수백 건이 되고, 그 합계를 모든 AI
     * 호출 직전에 구한다 — 호출 수만큼 전량 조회가 반복된다.
     *
     * <p>{@code COALESCE} 가 필요한 이유는 대상 행이 없을 때 {@code SUM} 이 null 을 주기 때문이다. 첫 호출은
     * 항상 그 상태이므로 예외 경로가 아니라 정상 경로다.
     */
    @Query(
            """
            SELECT COALESCE(SUM(u.inputTokens + u.outputTokens), 0)
            FROM AiUsageRecordJpaEntity u
            WHERE u.skeletonId = :skeletonId
            """)
    long sumTokensBySkeletonId(@Param("skeletonId") Long skeletonId);

    @Query(
            """
            SELECT COALESCE(SUM(u.inputTokens + u.outputTokens), 0)
            FROM AiUsageRecordJpaEntity u
            WHERE u.usageDate = :usageDate
            """)
    long sumTokensOn(@Param("usageDate") LocalDate usageDate);
}
