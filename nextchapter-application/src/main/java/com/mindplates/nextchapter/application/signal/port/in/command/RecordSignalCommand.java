package com.mindplates.nextchapter.application.signal.port.in.command;

import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.signal.model.SignalType;
import java.util.Map;

/**
 * @param chapterVersion 클라이언트가 <b>실제로 받은</b> 버전. 서버가 최신 버전을 다시 읽어 채우지 않는
 *     이유는, 사용자가 읽는 사이에 수정이 반영될 수 있고 그러면 구버전에 대한 반응이 새 버전의 실패로
 *     집계되기 때문이다
 */
public record RecordSignalCommand(
        Long chapterId,
        int chapterVersion,
        String blockId,
        DeliveryFormat format,
        SignalType type,
        Map<String, Object> payload) {}
