package com.mindplates.nextchapter.domain.chapter.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 챕터 본문 전체. 순서 있는 블록 목록이다.
 *
 * <p>ID 중복을 생성 시점에 막는 것이 이 타입의 존재 이유다. 같은 ID 가 두 블록에 있으면 신호가 두
 * 지점에 겹쳐 붙고, 그 상태는 <b>에러 없이</b> 집계를 오염시킨다 — 조회도 렌더링도 정상으로 보인다.
 */
public record BlockDocument(List<Block> blocks) {

    public BlockDocument {
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException("블록 문서가 비어 있습니다.");
        }
        Set<String> seen = new HashSet<>();
        for (Block block : blocks) {
            if (!seen.add(block.id())) {
                throw new IllegalArgumentException("블록 ID 가 중복됩니다: " + block.id());
            }
            // 공용 문서에 보충 블록 ID 가 들어오면 개인화 신호가 공용 블록 신호에 섞인다.
            // 그 오염은 에러 없이 집계를 틀리게 하므로 저장 경로에서 막는다.
            if (!BlockIds.isBody(block.id())) {
                throw new IllegalArgumentException("공용 블록 문서에는 본문 ID 만 올 수 있습니다: " + block.id());
            }
        }
        blocks = List.copyOf(blocks);
    }

    public static BlockDocument of(Block... blocks) {
        return new BlockDocument(List.of(blocks));
    }

    public Optional<Block> findById(String blockId) {
        return blocks.stream().filter(block -> block.id().equals(blockId)).findFirst();
    }

    public Set<String> blockIds() {
        return blocks.stream().map(Block::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** 지금까지 쓰인 최대 순번. 카운터가 뒤처졌을 때 따라잡는 기준이다. */
    public int maxSequence() {
        return blocks.stream()
                .mapToInt(block -> BlockIds.sequenceOf(block.id()))
                .max()
                .orElse(0);
    }

    /**
     * 웹 렌더러가 보는 블록. {@code narration} 처럼 특정 형태에서만 쓰이는 블록을 걸러낸다.
     *
     * <p>문서를 형태별로 나누지 않고 걸러 보는 이유가 여기 있다 — 소스는 하나여야 여러 사람의 반응을
     * 겹쳐 볼 수 있고, 소스가 하나여야 여러 형태로 뻗을 수 있다.
     */
    public List<Block> webBlocks() {
        return blocksFor(DeliveryFormat.WEB);
    }

    /**
     * 그 형태의 렌더러가 쓰는 블록.
     *
     * <p>형태별로 문서를 나누지 않고 <b>같은 문서를 걸러 보는</b> 이유가 여기 있다 — 소스가 하나여야 여러
     * 사람의 반응을 겹쳐 볼 수 있고, 소스가 하나여야 여러 형태로 뻗을 수 있다.
     */
    public List<Block> blocksFor(DeliveryFormat format) {
        return blocks.stream().filter(block -> format.includes(block.type())).toList();
    }

    /** 음성·자막의 원천. 영상 렌더러가 쓴다. */
    public List<Block> narrationBlocks() {
        return blocks.stream()
                .filter(block -> block.type() == BlockType.NARRATION)
                .toList();
    }

    /**
     * 출처를 달고 있는 블록. 하나도 없으면 검증 패스가 검사할 대상이 없다 — 그 상태는 "검증을 통과했다"와
     * 구분되지 않으므로 생성 시점에 확인해야 한다.
     */
    public List<Block> sourcedBlocks() {
        return blocks.stream().filter(Block::hasSources).toList();
    }

    public List<Block> quizBlocks() {
        return blocks.stream().filter(block -> block.type() == BlockType.QUIZ).toList();
    }

    /**
     * 새 버전의 블록 목록에 ID 를 확정한다 — 승계 규칙의 실행 지점.
     *
     * <p>모델이 돌려준 ID 가 기존 문서에 있으면 승계하고, 없거나 문서 안에서 중복이면 카운터에서 새
     * ID 를 발급한다. 잘못된 ID 를 통과시키는 것보다 신호가 새 블록에서 다시 시작하는 편이 낫다 —
     * 전자는 다른 블록의 신호를 오염시킨다.
     *
     * @param previous 이전 버전. 신규 생성이면 null
     * @param nextSequence 다음에 발급할 카운터 값 (챕터에 저장돼 있다)
     * @return 확정된 문서와, 다 쓴 뒤의 카운터 값
     */
    public static Succession succeed(List<ProposedBlock> proposed, BlockDocument previous, int nextSequence) {
        if (proposed == null || proposed.isEmpty()) {
            throw new IllegalArgumentException("확정할 블록이 없습니다.");
        }
        Set<String> inheritable = previous == null ? Set.of() : previous.blockIds();
        Set<String> used = new HashSet<>();
        List<Block> resolved = new ArrayList<>(proposed.size());
        int sequence = Math.max(nextSequence, previous == null ? 1 : previous.maxSequence() + 1);

        for (ProposedBlock block : proposed) {
            String rawId = block.rawId();
            boolean succeeds = rawId != null && inheritable.contains(rawId) && used.add(rawId);
            String blockId = succeeds ? rawId : BlockIds.of(sequence++);
            if (!succeeds) {
                used.add(blockId);
            }
            resolved.add(block.toBlock(blockId));
        }
        return new Succession(new BlockDocument(resolved), sequence);
    }

    /** ID 승계 결과. {@code nextSequence} 를 챕터에 되돌려 저장해야 재사용이 막힌다. */
    public record Succession(BlockDocument document, int nextSequence) {}
}
