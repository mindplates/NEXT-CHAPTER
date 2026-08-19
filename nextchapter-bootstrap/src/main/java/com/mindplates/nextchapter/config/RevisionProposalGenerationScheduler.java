package com.mindplates.nextchapter.config;

import com.mindplates.nextchapter.application.admin.port.in.GenerateRevisionProposalsUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 수정안 생성 스윕.
 *
 * <p>Kafka 대신 폴링인 이유는 발생 빈도다 — 임계치를 넘는 트리거는 신호처럼 매초 쌓이지 않고,
 * AI 호출 하나가 상대적으로 비싸다. 새 토픽·컨슈머 체인을 두는 비용이 폴링 지연보다 크다.
 * {@code FOR UPDATE SKIP LOCKED} 로 잠그므로 인스턴스가 여러 개여도 같은 트리거를 두 번 처리하지
 * 않는다.
 */
@Component
public class RevisionProposalGenerationScheduler {

    private final GenerateRevisionProposalsUseCase generateRevisionProposalsUseCase;
    private final int limit;

    public RevisionProposalGenerationScheduler(
            GenerateRevisionProposalsUseCase generateRevisionProposalsUseCase,
            @Value("${nextchapter.collective.revision.poll-limit:20}") int limit) {
        this.generateRevisionProposalsUseCase = generateRevisionProposalsUseCase;
        this.limit = limit;
    }

    @Scheduled(fixedDelayString = "${nextchapter.collective.revision.poll-interval-ms:30000}")
    public void generate() {
        generateRevisionProposalsUseCase.generatePending(limit);
    }
}
