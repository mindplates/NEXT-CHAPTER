package com.mindplates.nextchapter.config;

import com.mindplates.nextchapter.application.generation.port.in.PollAiBatchUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 제출한 배치를 주기적으로 조회한다.
 *
 * <p>폴링인 이유는 벤더가 완료를 알려 주지 않기 때문이다 — 웹훅이 없다. 최대 24시간까지 걸리므로 주기가
 * 길어도 되고, 짧게 돌면 대부분의 주기가 "아직 처리 중"을 확인하는 데만 쓰인다. 기본값 1분은 완료 후 반영
 * 지연이 그만큼이라는 뜻이고, 하루 단위 대기에 비하면 무의미한 크기다.
 *
 * <p>주기를 조립 모듈에 두는 이유는 "언제 부르는가"가 기동 절차의 관심사이기 때문이다. 유스케이스는
 * "끝난 배치를 반영한다"만 안다.
 */
@Component
public class AiBatchPollScheduler {

    private static final Logger log = LoggerFactory.getLogger(AiBatchPollScheduler.class);

    private final PollAiBatchUseCase pollAiBatchUseCase;

    public AiBatchPollScheduler(PollAiBatchUseCase pollAiBatchUseCase) {
        this.pollAiBatchUseCase = pollAiBatchUseCase;
    }

    @Scheduled(fixedDelayString = "${nextchapter.generation.batch.poll-interval-ms:60000}")
    public void pollSubmitted() {
        try {
            int applied = pollAiBatchUseCase.pollSubmitted();
            if (applied > 0) {
                log.info("[배치] 배치 {}건을 반영했다", applied);
            }
        } catch (RuntimeException e) {
            // 폴링 스레드가 죽으면 제출한 배치가 영원히 반영되지 않는다. 예외를 밖으로 내보내지 않는다.
            log.error("[배치] 폴링 주기 실패, 다음 주기에 다시 시도한다: {}", e.getMessage());
        }
    }
}
