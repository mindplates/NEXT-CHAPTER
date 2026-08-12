package com.mindplates.nextchapter.application.signal.service;

import com.mindplates.nextchapter.application.signal.port.in.PublishSignalsUseCase;
import com.mindplates.nextchapter.application.signal.port.out.LoadSignalPort;
import com.mindplates.nextchapter.application.signal.port.out.SaveSignalPort;
import com.mindplates.nextchapter.application.signal.port.out.SignalPublisherPort;
import com.mindplates.nextchapter.domain.signal.model.Signal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신호 → 집단 루프.
 *
 * <p>발행과 발행 표시를 <b>같은 트랜잭션</b>에 둔다. 표시가 커밋되지 않으면 다음 폴링이 같은 신호를 다시
 * 집어 가는데, 그게 옳은 방향이다 — 집계 컨슈머가 멱등하면 <b>중복 발행은 고칠 수 있고 누락은 아니다.</b>
 * 반대로 표시를 먼저 커밋하고 발행하면 발행 실패가 곧 유실이 되고, 그 유실은 "임계치가 트리거되지
 * 않는다"로 조용히 나타난다.
 *
 * <p>실패한 신호 하나가 뒤의 전부를 막지 않게 건별로 격리한다. 표시하지 않은 채 넘어가므로 다음 폴링이
 * 다시 시도한다.
 */
@Service
@RequiredArgsConstructor
public class SignalPublishService implements PublishSignalsUseCase {

    private static final Logger log = LoggerFactory.getLogger(SignalPublishService.class);

    private final LoadSignalPort loadSignalPort;
    private final SaveSignalPort saveSignalPort;
    private final SignalPublisherPort signalPublisherPort;

    @Override
    @Transactional
    public int publishPending(int limit) {
        List<Signal> claimed = loadSignalPort.claimUnpublished(limit);
        int published = 0;
        for (Signal signal : claimed) {
            try {
                signalPublisherPort.publish(signal);
                saveSignalPort.markPublished(signal.id());
                published++;
            } catch (RuntimeException e) {
                log.error("[신호] 발행 실패, 다음 폴링에서 다시 시도한다 id={} type={}: {}", signal.id(), signal.type(), e.getMessage());
            }
        }
        if (published > 0) {
            log.debug("[신호] {}건 발행", published);
        }
        return published;
    }
}
