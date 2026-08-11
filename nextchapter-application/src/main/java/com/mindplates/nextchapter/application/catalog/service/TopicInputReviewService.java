package com.mindplates.nextchapter.application.catalog.service;

import com.mindplates.nextchapter.application.catalog.port.in.ReviewTopicInputUseCase;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicInputLogPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveTopicInputLogPort;
import com.mindplates.nextchapter.application.catalog.view.TopicInputLogView;
import com.mindplates.nextchapter.common.PagedResult;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputLog;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputResolution;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미매핑 입력 검토 (P1.6).
 *
 * <p>여기서 카탈로그를 직접 고치지 않는다. 검토 결과는 "봤다"는 표시와 메모뿐이고, 도메인·분야·주제를
 * 추가하는 것은 카탈로그 CRUD 의 일이다. 나누는 이유는 감사 추적이다 — 무엇을 보고 무엇을 만들었는지가
 * 두 기록으로 남아야 나중에 되짚을 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TopicInputReviewService implements ReviewTopicInputUseCase {

    private final LoadTopicInputLogPort loadTopicInputLogPort;
    private final SaveTopicInputLogPort saveTopicInputLogPort;

    @Override
    @Transactional(readOnly = true)
    public PagedResult<TopicInputLogView> list(
            List<TopicInputResolution> resolutions, boolean onlyUnreviewed, int page, int size) {
        PagedResult<TopicInputLog> found = loadTopicInputLogPort.search(resolutions, onlyUnreviewed, page, size);
        return new PagedResult<>(found.items().stream().map(this::toView).toList(), found.total());
    }

    @Override
    public TopicInputLogView markReviewed(Long id, String reviewer, String note) {
        TopicInputLog found =
                loadTopicInputLogPort.findById(id).orElseThrow(() -> new EntityNotFoundException("주제 입력 로그", id));
        return toView(saveTopicInputLogPort.save(found.reviewed(reviewer, note)));
    }

    /**
     * 같은 표현이 몇 번 들어왔는지를 행마다 붙인다. 반복되는 미매핑 입력이 카탈로그의 빈 곳을 가장
     * 직접적으로 가리키므로, 검토 우선순위가 이 값으로 정해진다.
     */
    private TopicInputLogView toView(TopicInputLog log) {
        return new TopicInputLogView(
                log.id(),
                log.rawInput(),
                log.resolution(),
                log.matchSource(),
                log.matchedTopicId(),
                log.proposedTopicName(),
                loadTopicInputLogPort.countByNormalized(log.normalized()),
                log.userId(),
                log.isReviewed(),
                log.reviewedBy(),
                log.reviewNote(),
                log.createdAt());
    }
}
