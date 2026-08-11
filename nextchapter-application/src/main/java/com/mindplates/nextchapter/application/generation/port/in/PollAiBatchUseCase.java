package com.mindplates.nextchapter.application.generation.port.in;

/**
 * 제출한 배치를 조회하고 끝난 것을 반영한다.
 *
 * <p>폴링인 이유는 벤더가 완료를 알려 주지 않기 때문이다(웹훅이 없다). 최대 24시간까지 걸리므로 주기는
 * 길어도 된다 — 짧게 돌면 대부분의 주기가 "아직 처리 중"을 확인하는 데만 쓰인다.
 *
 * @return 이번에 반영한 배치 수
 */
public interface PollAiBatchUseCase {

    int pollSubmitted();
}
