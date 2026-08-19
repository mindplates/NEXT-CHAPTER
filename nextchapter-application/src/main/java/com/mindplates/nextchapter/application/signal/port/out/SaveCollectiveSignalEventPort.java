package com.mindplates.nextchapter.application.signal.port.out;

import com.mindplates.nextchapter.domain.signal.model.CollectiveSignalEvent;

public interface SaveCollectiveSignalEventPort {

    /**
     * {@code signalId} 로 멱등 저장한다 — 이미 있으면 아무 것도 하지 않는다.
     *
     * @return 새로 저장됐으면 true, 이미 있어 건너뛰었으면 false
     */
    boolean saveIfAbsent(CollectiveSignalEvent event);
}
