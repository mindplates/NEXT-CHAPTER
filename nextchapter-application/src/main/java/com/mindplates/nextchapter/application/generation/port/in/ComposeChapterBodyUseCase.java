package com.mindplates.nextchapter.application.generation.port.in;

import java.util.Optional;

/**
 * 본문 생성의 <b>프롬프트 조립</b>과 <b>응답 반영</b>을 나눠 노출한다.
 *
 * <p>개별 호출과 배치가 이 둘을 공유해야 한다. 배치용 프롬프트를 따로 만들면 두 경로가 서로 다른 본문을
 * 만들고, 그 차이는 어느 경로로 생성됐는지 모르는 상태에서 콘텐츠 품질 차이로만 나타난다 — 집단 신호를
 * 겹쳐 보는 전제가 무너진다.
 *
 * <p>반영도 한 곳이어야 한다. 파싱·출처 검사·블록 ID 승계·버전 증가가 두 곳에 복제되면 한쪽만 고치는 순간
 * 조용히 어긋난다.
 */
public interface ComposeChapterBodyUseCase {

    /**
     * 이 챕터의 본문 생성 프롬프트.
     *
     * @return 이미 본문이 있으면 비어 있다 — 배치에 넣지 않아야 한다. 중복 생성은 수정이 아니라 "왜 바뀌었는지
     *     설명할 수 없는 버전"을 남긴다
     */
    Optional<ChapterBodyPrompt> promptFor(Long skeletonId, Long chapterId);

    /** 응답 본문을 파싱해 챕터 본문으로 기록한다. */
    void apply(Long chapterId, String responseText);

    record ChapterBodyPrompt(String systemPrompt, String userPrompt, int maxTokens) {}
}
