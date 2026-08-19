package com.mindplates.nextchapter.application.admin.port.out;

import com.mindplates.nextchapter.domain.admin.model.RevisionProposal;

public interface SaveRevisionProposalPort {

    RevisionProposal save(RevisionProposal proposal);
}
