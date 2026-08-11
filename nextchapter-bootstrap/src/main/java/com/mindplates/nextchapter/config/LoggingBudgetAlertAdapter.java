package com.mindplates.nextchapter.config;

import com.mindplates.nextchapter.application.admin.port.out.BudgetAlertPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 예산 초과 알림의 잠정 구현 — 로그로 남긴다.
 *
 * <p>어댑터 모듈이 아니라 bootstrap 에 두는 이유는 <b>외부 시스템이 없기 때문이다</b>. 어댑터 모듈은 외부
 * 시스템별로 나누기로 했고(저장소·메시징·AI 프로바이더), 로깅에는 붙일 시스템이 없다. 메일이나 메신저가
 * 정해지면 그때 해당 어댑터 모듈로 옮긴다 — 포트가 이미 있으므로 호출부는 바뀌지 않는다.
 *
 * <p>{@code ERROR} 로 찍는다. 예산 초과는 운영자가 상한을 올리거나 생성을 포기하는 <b>결정을 해야</b>
 * 풀리는 상태이고, {@code WARN} 은 대개 아무도 보지 않는다.
 */
@Component
public class LoggingBudgetAlertAdapter implements BudgetAlertPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingBudgetAlertAdapter.class);

    @Override
    public void budgetExceeded(Long skeletonId, String scope, long used, long limit) {
        log.error(
                "[예산초과] {} 상한 도달 — 생성을 중단합니다. skeletonId={} 사용={} 상한={}",
                scope,
                skeletonId == null ? "-" : skeletonId,
                used,
                limit);
    }
}
