package com.mindplates.nextchapter.application.admin.port.out;

import com.mindplates.nextchapter.domain.admin.model.RevisionProposal;
import java.util.Optional;

public interface LoadRevisionProposalPort {

    /**
     * 이 트리거로 이미 제안이 만들어졌는지. Kafka 는 at-least-once 라 같은 트리거 이벤트가 다시 올 수
     * 있고, 그때 다시 생성하면 같은 문제에 대한 제안이 중복된다.
     */
    Optional<RevisionProposal> findByTriggerId(Long triggerId);
}
