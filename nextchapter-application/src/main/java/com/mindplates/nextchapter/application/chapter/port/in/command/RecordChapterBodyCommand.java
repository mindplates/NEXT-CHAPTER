package com.mindplates.nextchapter.application.chapter.port.in.command;

import com.mindplates.nextchapter.domain.chapter.model.ChapterVersionSource;
import com.mindplates.nextchapter.domain.chapter.model.ProposedBlock;
import java.util.List;

/**
 * 새 본문을 기록한다.
 *
 * <p>{@link ProposedBlock} 을 받는 것이 중요하다 — 호출자가 ID 를 정하지 않는다. ID 확정은 승계
 * 규칙이 하고, 그 규칙이 한 곳에만 있어야 신호가 끊기지 않는다.
 */
public record RecordChapterBodyCommand(
        List<ProposedBlock> blocks, ChapterVersionSource source, String changeSummary, String actor) {}
