package com.mindplates.nextchapter.adapter.in.web.signal.dto;

import com.mindplates.nextchapter.application.signal.port.in.command.RecordSignalCommand;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.signal.model.SignalType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * @param chapterVersion 클라이언트가 <b>실제로 받은</b> 버전. 서버가 최신 버전으로 채우지 않는 이유는,
 *     사용자가 읽는 사이에 수정이 반영될 수 있고 그러면 구버전에 대한 반응이 새 버전의 실패로 집계된다
 * @param blockId 신호가 붙는 지점. 소비 진행({@code PROGRESS})만 없어도 된다
 * @param payload 타입별 구조 — 퀴즈는 {@code correct}, 질문·오류 신고는 {@code text}, 진행은
 *     {@code percent}. 필수 키 검증은 도메인이 한다
 */
public record RecordSignalRequest(
        @NotNull @Min(1) Integer chapterVersion,
        String blockId,
        DeliveryFormat format,
        @NotNull SignalType type,
        Map<String, Object> payload) {

    public RecordSignalCommand toCommand(Long chapterId) {
        return new RecordSignalCommand(chapterId, chapterVersion, blockId, format, type, payload);
    }
}
