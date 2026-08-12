package com.mindplates.nextchapter.application.chapter.port.in;

import com.mindplates.nextchapter.application.chapter.view.ComposedChapterView;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;

/**
 * 사용자의 챕터 조회. 공용 본문에 그 사람의 레이어를 합성해 내린다.
 *
 * <p>검수용 조회({@link GetChapterUseCase})와 나눈 이유는 두 가지다. 사용자에게는 <b>published 뼈대만</b>
 * 보여야 하고, 합성은 호출자가 누구인지에 의존한다 — 운영자 화면은 반대로 <b>합성되지 않은</b> 공용 본문을
 * 봐야 한다(그것이 여러 사람이 공유하는 실체다).
 */
public interface ComposeChapterUseCase {

    ComposedChapterView compose(Long userId, Long chapterId, DeliveryFormat format);
}
