package com.mindplates.nextchapter.application.signal.port.out;

import com.mindplates.nextchapter.domain.signal.model.Signal;

public interface SaveSignalPort {

    Signal save(Signal signal);

    void markPublished(Long signalId);
}
