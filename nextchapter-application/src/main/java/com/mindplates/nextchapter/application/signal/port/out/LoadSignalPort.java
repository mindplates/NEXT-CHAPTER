package com.mindplates.nextchapter.application.signal.port.out;

import com.mindplates.nextchapter.domain.signal.model.Signal;
import java.util.List;

public interface LoadSignalPort {

    /**
     * 미발행 신호를 <b>잠그고</b> 집어 온다.
     *
     * <p>잠그지 않으면 인스턴스 두 개가 같은 신호를 발행해 집계가 두 번 센다. 잠그되 건너뛰지 않으면
     * 한쪽이 다른 쪽을 기다려 처리량이 1로 떨어진다 — 둘을 같이 써야 한다.
     */
    List<Signal> claimUnpublished(int limit);

    /** 이 사용자가 이 챕터에서 남긴 신호. 개인 루프가 다음 챕터를 결정할 때 읽는다. */
    List<Signal> findByUserAndChapter(Long userId, Long chapterId);
}
