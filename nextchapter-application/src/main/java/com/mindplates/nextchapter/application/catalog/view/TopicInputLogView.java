package com.mindplates.nextchapter.application.catalog.view;

import com.mindplates.nextchapter.domain.catalog.model.TopicInputResolution;
import com.mindplates.nextchapter.domain.catalog.model.TopicMatchSource;
import java.time.LocalDateTime;

/**
 * 검토 화면의 한 행.
 *
 * @param sameInputCount 같은 정규화 표현이 들어온 횟수. 반복되는 미매핑 입력이 카탈로그의 빈 곳을
 *     가장 직접적으로 가리키므로, 검토 우선순위가 이 값으로 정해진다
 */
public record TopicInputLogView(
        Long id,
        String rawInput,
        TopicInputResolution resolution,
        TopicMatchSource matchSource,
        Long matchedTopicId,
        String proposedTopicName,
        long sameInputCount,
        String userId,
        boolean reviewed,
        String reviewedBy,
        String reviewNote,
        LocalDateTime createdAt) {}
