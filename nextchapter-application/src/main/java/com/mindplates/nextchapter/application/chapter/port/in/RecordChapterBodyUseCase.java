package com.mindplates.nextchapter.application.chapter.port.in;

import com.mindplates.nextchapter.application.chapter.port.in.command.RecordChapterBodyCommand;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersion;

/**
 * 본문 생성·수정의 유일한 기록 경로.
 *
 * <p>생성(③ 본문 생성)과 수정(집단 루프·검수·검증 패스)이 같은 경로를 쓴다. 나누면 ID 승계와 버전
 * 증가 규칙이 두 곳에 복제되고, 한쪽만 고치는 순간 조용히 어긋난다.
 */
public interface RecordChapterBodyUseCase {

    ChapterVersion record(Long chapterId, RecordChapterBodyCommand command);
}
