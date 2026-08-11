package com.mindplates.nextchapter.domain.chapter.model;

import com.mindplates.nextchapter.common.text.Strings;

/**
 * 그래프 생성 단계가 제안한 챕터. 아직 ID 가 없어 <b>키로 서로를 가리킨다.</b>
 *
 * <p>관계를 ID 로 표현할 수 없는 이유는 순서다 — 챕터를 저장해야 ID 가 생기는데, 관계는 같은 응답에
 * 함께 온다. 키를 쓰면 모델이 사람이 읽을 수 있는 식별자로 그래프를 말할 수 있고, 저장 후 키→ID 로
 * 한 번 옮기면 된다.
 */
public record ProposedChapter(String key, String title, String summary, int sortOrder) {

    public ProposedChapter {
        key = Strings.requireMaxLength(Strings.requireText(key, "챕터 키"), 120, "챕터 키");
        title = Strings.requireMaxLength(Strings.requireText(title, "챕터 제목"), 300, "챕터 제목");
        summary = Strings.trimToNull(summary);
    }
}
