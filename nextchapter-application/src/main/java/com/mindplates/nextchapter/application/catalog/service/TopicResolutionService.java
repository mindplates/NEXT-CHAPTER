package com.mindplates.nextchapter.application.catalog.service;

import com.mindplates.nextchapter.application.catalog.port.in.CreateCatalogTopicUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.EmbedTopicUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.ManageTopicAliasUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.ResolveTopicInputUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.SearchSimilarTopicsUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.command.CreateCatalogTopicCommand;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogDomainPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogFieldPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadCatalogTopicPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicAliasPort;
import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicInputLogPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveTopicInputLogPort;
import com.mindplates.nextchapter.application.catalog.view.TopicResolutionView;
import com.mindplates.nextchapter.application.catalog.view.TopicSimilarityView;
import com.mindplates.nextchapter.application.generation.service.AiGateway;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.common.text.Strings;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputLog;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputResolution;
import com.mindplates.nextchapter.domain.catalog.model.TopicMatchSource;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자유 입력 → 하나의 뼈대.
 *
 * <p>수렴 경로가 세 단계로 겹쳐 있고, 앞에서 끝날수록 싸다.
 *
 * <ol>
 *   <li><b>별칭 정확 일치</b> — 모델을 부르지 않는다. 비용도 지연도 0이고 결과가 결정적이다
 *   <li><b>LLM 단계적 매핑</b> — 도메인 → 분야 → 주제. 각 단계 후보를 수십 개로 유지한다
 *   <li><b>임베딩 유사도 후보 + 사용자 확인</b> — 사용자가 통합의 마지막 판정자다
 * </ol>
 *
 * <p><b>이 서비스는 신규 주제를 스스로 만들지 않는다.</b> 만드는 것은 사용자가 후보를 모두 거절한
 * 경우뿐이다. 자동 생성으로 흘리면 오매칭 방지망이 없고, 운영자 승인 대기열로 보내면 사용자가 그
 * 자리에서 학습을 시작할 수 없다.
 *
 * <p>신규 <b>도메인·분야</b>는 어느 경로로도 만들지 않는다. 층별 좁히기는 상위 계층이 사람의 판단으로만
 * 늘어야 유지된다 — 입력마다 분야를 만들면 후보가 다시 수백 개가 되고 3단계로 나눈 이유가 사라진다.
 * 붙일 분야가 없는 입력은 운영자 검토(P1.6)로 간다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TopicResolutionService implements ResolveTopicInputUseCase {

    private static final Logger log = LoggerFactory.getLogger(TopicResolutionService.class);

    private static final int CANDIDATE_LIMIT = 5;
    private static final int MAPPING_MAX_TOKENS = 512;
    private static final int MAX_SLUG_ATTEMPTS = 50;

    private final LoadCatalogDomainPort loadCatalogDomainPort;
    private final LoadCatalogFieldPort loadCatalogFieldPort;
    private final LoadCatalogTopicPort loadCatalogTopicPort;
    private final LoadTopicAliasPort loadTopicAliasPort;
    private final LoadTopicInputLogPort loadTopicInputLogPort;
    private final SaveTopicInputLogPort saveTopicInputLogPort;
    private final LoadSkeletonPort loadSkeletonPort;
    private final CreateCatalogTopicUseCase createCatalogTopicUseCase;
    private final ManageTopicAliasUseCase manageTopicAliasUseCase;
    private final SearchSimilarTopicsUseCase searchSimilarTopicsUseCase;
    private final EmbedTopicUseCase embedTopicUseCase;
    private final AiGateway aiGateway;

    @Override
    public TopicResolutionView resolve(String rawInput, String userId) {
        String input = Strings.requireMaxLength(Strings.requireText(rawInput, "주제 입력"), 500, "주제 입력");

        Optional<CatalogTopic> exact = matchExactly(input);
        if (exact.isPresent()) {
            TopicInputLog saved = saveTopicInputLogPort.save(
                    TopicInputLog.matched(input, exact.get().id(), TopicMatchSource.ALIAS, userId));
            return matchedView(saved, exact.get());
        }

        Narrowing narrowing = narrowWithLlm(input);
        if (narrowing.topic() != null) {
            TopicInputLog saved = saveTopicInputLogPort.save(
                    TopicInputLog.matched(input, narrowing.topic().id(), TopicMatchSource.LLM, userId));
            return matchedView(saved, narrowing.topic());
        }

        return presentCandidates(input, narrowing, userId);
    }

    @Override
    public TopicResolutionView confirmCandidate(Long requestId, Long topicId) {
        TopicInputLog pending = requirePending(requestId);
        if (!pending.candidateTopicIds().contains(topicId)) {
            throw new InvalidOperationException("제시된 후보가 아닌 주제입니다.");
        }
        CatalogTopic topic =
                loadCatalogTopicPort.findById(topicId).orElseThrow(() -> new EntityNotFoundException("주제", topicId));

        TopicInputLog saved = saveTopicInputLogPort.save(pending.resolvedByUser(topicId));
        promoteToAlias(topicId, pending.rawInput());
        return matchedView(saved, topic);
    }

    /**
     * 후보를 모두 거절했다. 붙일 분야가 있으면 신규 주제를 만들고, 없으면 검토로 넘긴다.
     *
     * <p>거절이 곧 생성인 이 지점이 신규 뼈대 생성 비용의 방어선이다. 사용자가 후보를 하나씩 보고
     * 전부 아니라고 판정한 기록이 남아 있어야, 나중에 "이 뼈대는 왜 만들어졌나"에 답할 수 있다.
     */
    @Override
    public TopicResolutionView rejectCandidates(Long requestId) {
        TopicInputLog pending = requirePending(requestId);

        if (pending.proposedFieldId() == null) {
            TopicInputLog escalated = saveTopicInputLogPort.save(pending.escalatedToReview());
            log.info("[매핑] 붙일 분야가 없어 검토로 넘긴다 input=\"{}\"", pending.rawInput());
            return needsReviewView(escalated);
        }

        CatalogField field = loadCatalogFieldPort
                .findById(pending.proposedFieldId())
                .orElseThrow(() -> new EntityNotFoundException("분야", pending.proposedFieldId()));
        String name = pending.proposedTopicName() == null
                ? Strings.requireMaxLength(pending.rawInput(), 160, "주제 이름")
                : pending.proposedTopicName();

        CatalogTopic created = createCatalogTopicUseCase.create(
                new CreateCatalogTopicCommand(field.id(), uniqueSlug(pending), name, null, 0));
        promoteToAlias(created.id(), pending.rawInput());
        embedQuietly(created.id());

        TopicInputLog saved = saveTopicInputLogPort.save(pending.resolvedByNewTopic(created.id()));
        log.info("[매핑] 후보 전부 거절 → 신규 주제 등록 topicId={} slug={}", created.id(), created.slug());
        return matchedView(saved, created);
    }

    // --- 1단계: 별칭·주제명 정확 일치 --------------------------------------

    /**
     * 별칭 테이블을 먼저 보고, 없으면 주제명을 정규화해 비교한다.
     *
     * <p>주제명 비교를 메모리에서 하는 이유는 정규화 컬럼을 주제 테이블에 더하지 않기 위해서다. 주제
     * 수가 카탈로그 크기에 묶여 있고(초기 30~50개), 이 경로가 실패하면 어차피 LLM 호출이 뒤따르므로
     * 목록 스캔 비용은 무시할 만하다. 컬럼을 더하면 정규화 규칙이 바뀔 때 전량 갱신 배치가 하나 더 생긴다.
     */
    private Optional<CatalogTopic> matchExactly(String input) {
        String normalized = Strings.normalizeForMatch(input);
        if (normalized == null) {
            return Optional.empty();
        }
        Optional<CatalogTopic> byAlias = loadTopicAliasPort
                .findByNormalized(normalized)
                .flatMap(alias -> loadCatalogTopicPort.findById(alias.topicId()));
        if (byAlias.isPresent()) {
            return byAlias;
        }
        return loadCatalogTopicPort.findAll().stream()
                .filter(topic -> normalized.equals(Strings.normalizeForMatch(topic.name())))
                .findFirst();
    }

    // --- 2단계: LLM 단계적 매핑 --------------------------------------------

    /**
     * 도메인 → 분야 → 주제 순으로 좁힌다. 어느 단계에서 멈췄는지가 다음 경로를 정한다 — 분야까지
     * 좁혀졌다면 신규 주제를 붙일 자리가 있고, 그러지 못했다면 없다.
     *
     * <p>LLM 호출 실패를 요청 실패로 번지게 하지 않는다. 키가 없거나 벤더가 죽었을 때도 사용자는
     * 후보 제시·검토 경로로 흘러가야 한다.
     */
    private Narrowing narrowWithLlm(String input) {
        List<CatalogDomain> domains = loadCatalogDomainPort.findAll();
        if (domains.isEmpty()) {
            return Narrowing.nothing();
        }
        try {
            Optional<CatalogDomain> domain =
                    pick(TopicMappingPrompts.domainPrompt(domains, input), domains, CatalogDomain::slug);
            if (domain.isEmpty()) {
                return Narrowing.nothing();
            }

            List<CatalogField> fields =
                    loadCatalogFieldPort.findByDomainId(domain.get().id());
            if (fields.isEmpty()) {
                return Narrowing.nothing();
            }
            Optional<CatalogField> field =
                    pick(TopicMappingPrompts.fieldPrompt(domain.get(), fields, input), fields, CatalogField::slug);
            if (field.isEmpty()) {
                return Narrowing.nothing();
            }

            List<CatalogTopic> topics =
                    loadCatalogTopicPort.findByFieldId(field.get().id());
            String response = ask(TopicMappingPrompts.topicPrompt(field.get(), topics, input));
            Optional<CatalogTopic> topic = TopicMappingPrompts.pickedSlug(response)
                    .flatMap(slug -> topics.stream()
                            .filter(candidate -> candidate.slug().equals(slug))
                            .findFirst());

            return new Narrowing(
                    field.get().id(),
                    topic.orElse(null),
                    TopicMappingPrompts.proposedName(response).orElse(null),
                    TopicMappingPrompts.proposedSlug(response).orElse(null));
        } catch (RuntimeException e) {
            log.warn("[매핑] LLM 단계적 매핑 실패, 후보 제시 경로로 넘어간다: {}", e.getMessage());
            return Narrowing.nothing();
        }
    }

    private <T> Optional<T> pick(String prompt, List<T> items, java.util.function.Function<T, String> slug) {
        return TopicMappingPrompts.pickedSlug(ask(prompt)).flatMap(picked -> items.stream()
                .filter(item -> slug.apply(item).equals(picked))
                .findFirst());
    }

    private String ask(String prompt) {
        return aiGateway
                .complete(AiStage.TOPIC_MAPPING, TopicMappingPrompts.SYSTEM_PROMPT, prompt, MAPPING_MAX_TOKENS)
                .text();
    }

    // --- 3단계: 후보 제시 ---------------------------------------------------

    private TopicResolutionView presentCandidates(String input, Narrowing narrowing, String userId) {
        List<TopicSimilarityView> candidates = searchQuietly(input);

        // 고를 후보도 없고 만들 자리도 없으면 사용자가 할 수 있는 일이 없다. 바로 검토로 보낸다.
        if (candidates.isEmpty() && narrowing.fieldId() == null) {
            TopicInputLog saved = saveTopicInputLogPort.save(
                    TopicInputLog.needsReview(input, narrowing.proposedName(), narrowing.proposedSlug(), userId));
            log.info("[매핑] 후보도 붙일 자리도 없어 검토로 넘긴다 input=\"{}\"", input);
            return needsReviewView(saved);
        }

        TopicInputLog saved = saveTopicInputLogPort.save(TopicInputLog.candidatesPresented(
                input,
                candidates.stream().map(TopicSimilarityView::topicId).toList(),
                narrowing.fieldId(),
                narrowing.proposedName(),
                narrowing.proposedSlug(),
                userId));

        return new TopicResolutionView(
                saved.id(),
                TopicInputResolution.CANDIDATES_PRESENTED,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                candidates,
                narrowing.fieldId() != null,
                narrowing.proposedName());
    }

    /** 임베딩 검색 실패가 매핑 전체를 막지 않게 한다 — 후보가 없는 것과 검색이 죽은 것은 같게 다뤄도 된다. */
    private List<TopicSimilarityView> searchQuietly(String input) {
        try {
            return searchSimilarTopicsUseCase.search(input, CANDIDATE_LIMIT);
        } catch (RuntimeException e) {
            log.warn("[매핑] 유사 주제 검색 실패: {}", e.getMessage());
            return List.of();
        }
    }

    // --- 공통 ---------------------------------------------------------------

    private TopicInputLog requirePending(Long requestId) {
        TopicInputLog pending = loadTopicInputLogPort
                .findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("주제 입력 요청", requestId));
        if (!pending.awaitsUserDecision()) {
            throw new InvalidOperationException("이미 처리된 요청입니다.");
        }
        return pending;
    }

    /**
     * 사용자가 판정한 표현을 별칭으로 승격시킨다. 다음에 같은 표현이 들어오면 1단계에서 끝나 모델을
     * 부르지 않는다 — 사람의 판단 한 번이 영구적인 결정적 경로가 된다.
     *
     * <p>실패를 삼키는 이유는 이것이 최적화이기 때문이다. 이미 다른 주제에 등록된 표현이면 등록하지
     * 않는 것이 옳고, 그 때문에 매핑 결과를 되돌릴 이유는 없다.
     */
    private void promoteToAlias(Long topicId, String rawInput) {
        try {
            manageTopicAliasUseCase.add(topicId, rawInput);
        } catch (RuntimeException e) {
            log.debug("[매핑] 별칭 승격 생략 topicId={} input=\"{}\": {}", topicId, rawInput, e.getMessage());
        }
    }

    /** 신규 주제는 즉시 임베딩한다 — 이 경로는 이미 AI 를 쓰고 있고, 다음 사용자가 유사도로 찾아야 한다. */
    private void embedQuietly(Long topicId) {
        try {
            embedTopicUseCase.embed(topicId);
        } catch (RuntimeException e) {
            log.warn("[매핑] 신규 주제 임베딩 실패, 재임베딩 배치가 집어간다 topicId={}: {}", topicId, e.getMessage());
        }
    }

    /**
     * slug 를 정한다. LLM 제안 → 이름의 ASCII 변환 → 입력 로그 ID 순으로 내려간다.
     *
     * <p>마지막 폴백이 필요한 이유는 한글 주제명에서 ASCII slug 를 만들 수 없기 때문이다. 로그 ID 를
     * 쓰면 불투명하지만 유일하고, 무엇보다 <b>그 slug 를 만든 입력으로 되짚을 수 있다.</b>
     */
    private String uniqueSlug(TopicInputLog pending) {
        String base = Optional.ofNullable(pending.proposedTopicSlug())
                .map(TopicResolutionService::asciiSlug)
                .filter(slug -> !slug.isEmpty())
                .or(() -> Optional.ofNullable(pending.proposedTopicName())
                        .map(TopicResolutionService::asciiSlug)
                        .filter(slug -> !slug.isEmpty()))
                .orElseGet(() -> asciiSlug(pending.rawInput()));
        if (base.isEmpty()) {
            base = "topic-" + pending.id();
        }

        for (int suffix = 0; suffix < MAX_SLUG_ATTEMPTS; suffix++) {
            String candidate = suffix == 0 ? base : base + "-" + (suffix + 1);
            if (loadCatalogTopicPort.findBySlug(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new InvalidOperationException("slug 를 정하지 못했습니다: " + base);
    }

    private static String asciiSlug(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+)|(-+$)", "");
    }

    private TopicResolutionView matchedView(TopicInputLog logRow, CatalogTopic topic) {
        CatalogField field = loadCatalogFieldPort.findById(topic.fieldId()).orElse(null);
        CatalogDomain domain = field == null
                ? null
                : loadCatalogDomainPort.findById(field.domainId()).orElse(null);
        return new TopicResolutionView(
                logRow.id(),
                logRow.resolution(),
                logRow.matchSource(),
                topic.id(),
                topic.name(),
                topic.slug(),
                field == null ? null : field.name(),
                domain == null ? null : domain.name(),
                loadSkeletonPort.findByTopicId(topic.id()).isPresent(),
                List.of(),
                false,
                null);
    }

    private static TopicResolutionView needsReviewView(TopicInputLog logRow) {
        return new TopicResolutionView(
                logRow.id(),
                TopicInputResolution.NEEDS_REVIEW,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                List.of(),
                false,
                logRow.proposedTopicName());
    }

    /** 단계적 매핑이 어디까지 좁혀졌는지. {@code fieldId} 가 null 이면 신규 주제를 붙일 자리가 없다. */
    private record Narrowing(Long fieldId, CatalogTopic topic, String proposedName, String proposedSlug) {

        static Narrowing nothing() {
            return new Narrowing(null, null, null, null);
        }
    }
}
