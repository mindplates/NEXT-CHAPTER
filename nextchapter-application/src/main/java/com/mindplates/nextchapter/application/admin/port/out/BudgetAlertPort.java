package com.mindplates.nextchapter.application.admin.port.out;

/**
 * 예산 초과 알림.
 *
 * <p>포트로 두는 이유는 실제 채널(메일·메신저)이 아직 없기 때문이다. 지금 구현은 로그이고, 그건 알림이라기
 * 보다 기록에 가깝다 — 그래도 경계를 만들어 두면 채널이 붙을 때 호출부가 흔들리지 않는다. 채널 결정은 M7 이다.
 *
 * <p>알림이 필요한 이유는 상한 초과가 <b>조용히 멈추는 실패</b>이기 때문이다. 생성이 중단됐다는 사실을
 * 운영자가 모르면 사용자는 영원히 완료 알림을 받지 못하고, 그 원인은 예산 설정에 있다.
 */
public interface BudgetAlertPort {

    void budgetExceeded(Long skeletonId, String scope, long used, long limit);
}
