package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicInputLogPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveTopicInputLogPort;
import com.mindplates.nextchapter.common.PagedResult;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputLog;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputResolution;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TopicInputLogPersistenceAdapter implements LoadTopicInputLogPort, SaveTopicInputLogPort {

    private final TopicInputLogJpaRepository logRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<TopicInputLog> findById(Long id) {
        return logRepository.findById(id).map(TopicInputLogPersistenceAdapter::toDomain);
    }

    /**
     * 최신 입력부터 본다. 검토 대기열의 관심사는 "지금 카탈로그에서 빠진 것"이고, 오래된 미매핑
     * 입력은 이미 다른 경로로 해결됐을 가능성이 높다.
     */
    @Override
    @Transactional(readOnly = true)
    public PagedResult<TopicInputLog> search(
            List<TopicInputResolution> resolutions, boolean onlyUnreviewed, int page, int size) {
        PageRequest pageRequest =
                PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "id"));
        List<TopicInputResolution> filter = resolutions == null || resolutions.isEmpty() ? null : resolutions;

        Page<TopicInputLogJpaEntity> found = onlyUnreviewed
                ? logRepository.findUnreviewed(filter, pageRequest)
                : logRepository.findAny(filter, pageRequest);
        return new PagedResult<>(
                found.getContent().stream()
                        .map(TopicInputLogPersistenceAdapter::toDomain)
                        .toList(),
                found.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public long countByNormalized(String normalized) {
        return normalized == null ? 0 : logRepository.countByNormalized(normalized);
    }

    @Override
    @Transactional
    public TopicInputLog save(TopicInputLog log) {
        return toDomain(logRepository.save(TopicInputLogJpaEntity.builder()
                .id(log.id())
                .rawInput(log.rawInput())
                .normalized(log.normalized())
                .resolution(log.resolution())
                .matchSource(log.matchSource())
                .matchedTopicId(log.matchedTopicId())
                .proposedFieldId(log.proposedFieldId())
                .proposedTopicName(log.proposedTopicName())
                .proposedTopicSlug(log.proposedTopicSlug())
                .candidateTopicIds(joinIds(log.candidateTopicIds()))
                .userId(log.userId())
                .reviewedAt(log.reviewedAt())
                .reviewedBy(log.reviewedBy())
                .reviewNote(log.reviewNote())
                .build()));
    }

    private static TopicInputLog toDomain(TopicInputLogJpaEntity entity) {
        return new TopicInputLog(
                entity.getId(),
                entity.getRawInput(),
                entity.getNormalized(),
                entity.getResolution(),
                entity.getMatchSource(),
                entity.getMatchedTopicId(),
                entity.getProposedFieldId(),
                entity.getProposedTopicName(),
                entity.getProposedTopicSlug(),
                splitIds(entity.getCandidateTopicIds()),
                entity.getUserId(),
                entity.getReviewedAt(),
                entity.getReviewedBy(),
                entity.getReviewNote(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private static String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
    }

    private static List<Long> splitIds(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(","))
                .map(String::trim)
                .map(Long::valueOf)
                .toList();
    }
}
