package com.mindplates.nextchapter.config;

import com.mindplates.nextchapter.application.signal.port.in.PublishSignalsUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 신호 발행 스윕.
 *
 * <p>커밋 시점을 알 수 없으므로 폴링이다 — outbox 퍼블리셔와 같은 이유. 개인 루프는 같은 트랜잭션에서
 * 이미 기록됐으므로 이 지연은 집단 루프에만 영향을 주고, 집단 루프는 누적 주기라 문제가 되지 않는다.
 */
@Component
public class SignalPublishScheduler {

    private final PublishSignalsUseCase publishSignalsUseCase;
    private final int limit;

    public SignalPublishScheduler(
            PublishSignalsUseCase publishSignalsUseCase,
            @Value("${nextchapter.kafka.signal.poll-limit:200}") int limit) {
        this.publishSignalsUseCase = publishSignalsUseCase;
        this.limit = limit;
    }

    @Scheduled(fixedDelayString = "${nextchapter.kafka.signal.poll-interval-ms:1000}")
    public void publish() {
        publishSignalsUseCase.publishPending(limit);
    }
}
