package com.mindplates.nextchapter.application.signal.port.out;

import com.mindplates.nextchapter.domain.signal.model.RevisionTrigger;
import java.util.List;

public interface LoadRevisionTriggerPort {

    /**
     * 아직 수정안이 생성되지 않은 트리거를 <b>잠그고</b> 집어 온다.
     *
     * <p>잠그지 않으면 인스턴스 두 개가 같은 트리거로 제안을 두 번 만들 수 있다 — AI 호출 비용이 두 배가
     * 되고, 같은 문제에 대한 제안이 승인 대기열에 중복으로 오른다.
     */
    List<RevisionTrigger> claimUnprocessed(int limit);
}
