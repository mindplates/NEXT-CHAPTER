package com.mindplates.nextchapter.application.signal.port.in;

import com.mindplates.nextchapter.application.signal.port.in.command.RecordSignalCommand;
import com.mindplates.nextchapter.application.signal.view.SignalView;

/**
 * 소비 중 발생한 신호를 기록한다. 퀴즈 응답 · 질문 · 오류 신고 · 소비 진행이 모두 이 경로를 지난다.
 *
 * <p>종류별로 유스케이스를 나누지 않는 이유는 <b>검증과 두 갈래 경로가 전부 같기</b> 때문이다 —
 * 공개 여부 확인, 소비한 버전 확인, 블록 존재 확인, Postgres 동기 기록, 집계 발행 대기. 나누면 그
 * 다섯 가지가 네 곳에 복제되고, 한 곳만 빠뜨려도 그 종류의 신호가 조용히 집계에서 빠진다.
 */
public interface RecordSignalUseCase {

    SignalView record(Long userId, RecordSignalCommand command);
}
