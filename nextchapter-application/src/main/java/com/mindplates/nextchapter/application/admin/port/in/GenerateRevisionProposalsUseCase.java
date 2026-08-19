package com.mindplates.nextchapter.application.admin.port.in;

public interface GenerateRevisionProposalsUseCase {

    /** 아직 처리되지 않은 트리거만큼 수정안을 생성한다. @return 생성한 건수 */
    int generatePending(int limit);
}
