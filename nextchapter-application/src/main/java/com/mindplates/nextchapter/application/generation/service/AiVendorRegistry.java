package com.mindplates.nextchapter.application.generation.service;

import com.mindplates.nextchapter.application.generation.port.out.EmbeddingClientPort;
import com.mindplates.nextchapter.application.generation.port.out.LlmBatchClientPort;
import com.mindplates.nextchapter.application.generation.port.out.LlmCompletionClientPort;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * 등록된 벤더 어댑터를 벤더 값으로 찾아 준다.
 *
 * <p>여기가 "AI 설정(admin)"과 "AI 호출(generation)"이 만나는 지점이다. 설정은 어느 벤더를 쓸지
 * 고르고, 호출은 그 벤더의 클라이언트를 필요로 한다. 레지스트리가 둘 사이에 있어 설정 화면이
 * 구현 클래스를 알지 않아도 되고, 호출부가 벤더 분기를 갖지 않아도 된다.
 *
 * <p>벤더를 추가하는 비용이 <b>구현 하나를 빈으로 등록</b>하는 것으로 끝나야 한다. 그래서 목록을
 * 하드코딩하지 않고 주입된 구현에서 역산한다.
 */
@Service
public class AiVendorRegistry {

    private final Map<AiVendor, LlmCompletionClientPort> completionClients = new EnumMap<>(AiVendor.class);
    private final Map<AiVendor, EmbeddingClientPort> embeddingClients = new EnumMap<>(AiVendor.class);
    private final Map<AiVendor, LlmBatchClientPort> batchClients = new EnumMap<>(AiVendor.class);

    public AiVendorRegistry(
            List<LlmCompletionClientPort> completionPorts,
            List<EmbeddingClientPort> embeddingPorts,
            List<LlmBatchClientPort> batchPorts) {
        completionPorts.forEach(port -> completionClients.put(port.vendor(), port));
        embeddingPorts.forEach(port -> embeddingClients.put(port.vendor(), port));
        batchPorts.forEach(port -> batchClients.put(port.vendor(), port));
    }

    public Optional<LlmCompletionClientPort> completionClient(AiVendor vendor) {
        return Optional.ofNullable(completionClients.get(vendor));
    }

    public Optional<EmbeddingClientPort> embeddingClient(AiVendor vendor) {
        return Optional.ofNullable(embeddingClients.get(vendor));
    }

    /**
     * 배치 클라이언트. <b>구현 존재 여부가 곧 능력 조회다</b> — 모든 벤더가 배치를 제공하지 않으므로,
     * 없으면 개별 호출 경로로 간다.
     */
    public Optional<LlmBatchClientPort> batchClient(AiVendor vendor) {
        return Optional.ofNullable(batchClients.get(vendor));
    }

    /** 완성 호출이 가능한 벤더. */
    public Set<AiVendor> completionVendors() {
        return Set.copyOf(completionClients.keySet());
    }

    /** 임베딩 호출이 가능한 벤더. */
    public Set<AiVendor> embeddingVendors() {
        return Set.copyOf(embeddingClients.keySet());
    }

    /** 배치 제출이 가능한 벤더. */
    public Set<AiVendor> batchVendors() {
        return Set.copyOf(batchClients.keySet());
    }

    /**
     * 어느 쪽으로든 쓸 수 있는 벤더 전체. 관리 화면의 선택지가 이 목록이어야 한다.
     *
     * <p>배치 전용 벤더는 없다 — 배치를 제공하는 벤더는 완성도 제공한다. 그래서 이 목록에 배치를 더하지
     * 않는다. 더하면 완성 어댑터가 없는 벤더를 저장할 수 있게 되고, 그건 저장 시점 거절이 뚫리는 것이다.
     */
    public Set<AiVendor> supportedVendors() {
        Set<AiVendor> all = new TreeSet<>(completionClients.keySet());
        all.addAll(embeddingClients.keySet());
        return all;
    }
}
