package com.mindplates.nextchapter.application.signal.port.in;

import com.mindplates.nextchapter.domain.signal.model.CollectiveSignalEvent;

public interface RecordCollectiveSignalUseCase {

    void record(CollectiveSignalEvent event);
}
