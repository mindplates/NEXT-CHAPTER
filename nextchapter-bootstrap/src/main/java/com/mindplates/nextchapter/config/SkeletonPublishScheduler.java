package com.mindplates.nextchapter.config;

import com.mindplates.nextchapter.application.generation.port.in.PublishReadySkeletonsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 공개 조건을 만족한 뼈대를 주기적으로 {@code PUBLISHED} 로 넘긴다.
 *
 * <p>마지막 챕터가 끝난 그 자리에서 판정하지 않는 이유는 조건에 <b>outbox 가 비었는가</b>가 들어가기
 * 때문이다. outbox 퍼블리셔는 폴링이라 그 순간에는 아직 발행이 남아 있고, 그때 판정하면 항상 실패한다 —
 * 그리고 한 번 실패한 판정을 다시 부르는 사람이 없으면 뼈대가 영원히 {@code GENERATING_ASSETS} 에 남는다.
 *
 * <p>outbox 주기보다 느리게 돈다. 더 빠르게 돌면 대부분의 주기가 "아직 발행이 남았다"를 확인하는 데만
 * 쓰인다.
 */
@Component
public class SkeletonPublishScheduler {

    private static final Logger log = LoggerFactory.getLogger(SkeletonPublishScheduler.class);

    private final PublishReadySkeletonsUseCase publishReadySkeletonsUseCase;

    public SkeletonPublishScheduler(PublishReadySkeletonsUseCase publishReadySkeletonsUseCase) {
        this.publishReadySkeletonsUseCase = publishReadySkeletonsUseCase;
    }

    @Scheduled(fixedDelayString = "${nextchapter.generation.publish-sweep-interval-ms:5000}")
    public void publishReady() {
        try {
            int published = publishReadySkeletonsUseCase.publishReady();
            if (published > 0) {
                log.info("[생성] 뼈대 {}개를 공개했다", published);
            }
        } catch (RuntimeException e) {
            // 스윕 스레드가 죽으면 이후 어떤 뼈대도 공개되지 않는다. 예외를 밖으로 내보내지 않는다.
            log.error("[생성] 공개 스윕 주기 실패, 다음 주기에 다시 시도한다: {}", e.getMessage());
        }
    }
}
