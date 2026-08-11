package com.mindplates.nextchapter.domain.chapter.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 그래프 생성 단계의 산출물 — 챕터 제안 + 관계 제안.
 *
 * <p>검증을 이 타입에 모으는 이유는 잘못된 그래프가 <b>조용히</b> 문제를 만들기 때문이다. 존재하지
 * 않는 키를 가리키는 관계, 선행 관계의 순환, 고립된 챕터는 저장은 성공하고 소비 시점에 "빈 추천"이나
 * "진입할 수 없는 챕터"로 나타난다. 그때는 이미 본문·자산 생성비가 다 든 뒤다.
 *
 * @param chapterCountBounds 챕터 수 하한·상한을 검사한다. 너무 적으면 주제를 덮지 못하고, 너무 많으면
 *     생성비가 뼈대 하나에 몰리면서 챕터당 신호가 얇아진다
 */
public record ProposedChapterGraph(List<ProposedChapter> chapters, List<ProposedRelation> relations) {

    /** 주제 하나를 덮는 데 필요한 최소치. 이보다 적으면 그래프라고 부를 것이 없다. */
    public static final int MIN_CHAPTERS = 4;

    /**
     * 상한. 넘으면 생성비가 뼈대 하나에 몰리고, 같은 사용자 수를 챕터가 나눠 가져 챕터당 신호가
     * 얇아진다 — 집단 루프가 임계치를 넘기 어려워진다.
     */
    public static final int MAX_CHAPTERS = 40;

    public ProposedChapterGraph {
        if (chapters == null || chapters.size() < MIN_CHAPTERS) {
            throw new IllegalArgumentException(
                    "챕터가 " + MIN_CHAPTERS + "개 이상이어야 합니다: " + (chapters == null ? 0 : chapters.size()));
        }
        if (chapters.size() > MAX_CHAPTERS) {
            throw new IllegalArgumentException("챕터가 " + MAX_CHAPTERS + "개를 넘습니다: " + chapters.size());
        }
        chapters = List.copyOf(chapters);
        relations = relations == null ? List.of() : List.copyOf(relations);

        Set<String> keys = requireUniqueKeys(chapters);
        requireKnownEndpoints(relations, keys);
        requireNoSelfRelation(relations);
        requireNoPrerequisiteCycle(chapters, relations);
    }

    public List<String> keys() {
        return chapters.stream().map(ProposedChapter::key).toList();
    }

    /**
     * 선행 관계가 하나도 없는 챕터 — 진입점.
     *
     * <p>하나도 없으면 순환이 있다는 뜻이고, 그건 생성 시점에 이미 걸러진다. 이 메서드는 개인 루프가
     * "처음 볼 챕터"를 고를 때의 후보를 만든다.
     */
    public List<String> entryPointKeys() {
        Set<String> hasPrerequisite = relations.stream()
                .filter(relation -> relation.relationType() == ChapterRelationType.PREREQUISITE)
                .map(ProposedRelation::toKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return keys().stream().filter(key -> !hasPrerequisite.contains(key)).toList();
    }

    private static Set<String> requireUniqueKeys(List<ProposedChapter> chapters) {
        Set<String> keys = new HashSet<>();
        for (ProposedChapter chapter : chapters) {
            if (!keys.add(chapter.key())) {
                throw new IllegalArgumentException("챕터 키가 중복됩니다: " + chapter.key());
            }
        }
        return keys;
    }

    /** 없는 키를 가리키는 관계를 남기면 그래프에 끊어진 간선이 생기고, 탐색이 조용히 막힌다. */
    private static void requireKnownEndpoints(List<ProposedRelation> relations, Set<String> keys) {
        for (ProposedRelation relation : relations) {
            if (!keys.contains(relation.fromKey())) {
                throw new IllegalArgumentException("관계가 없는 챕터를 가리킵니다: " + relation.fromKey());
            }
            if (!keys.contains(relation.toKey())) {
                throw new IllegalArgumentException("관계가 없는 챕터를 가리킵니다: " + relation.toKey());
            }
        }
    }

    private static void requireNoSelfRelation(List<ProposedRelation> relations) {
        for (ProposedRelation relation : relations) {
            if (relation.fromKey().equals(relation.toKey())) {
                throw new IllegalArgumentException("자기 자신에 대한 관계는 만들 수 없습니다: " + relation.fromKey());
            }
        }
    }

    /**
     * 선행 관계에 순환이 있으면 어느 챕터도 선행 조건을 만족시킬 수 없어 <b>진입점이 사라진다.</b>
     *
     * <p>이 실패는 조회 시점에 예외가 아니라 "추천할 챕터가 없음"으로 나타나므로 생성 시점에 막아야
     * 한다. {@link ChapterRelationType#RELATED} 와 {@link ChapterRelationType#DEEPENS} 는 순환이
     * 문제가 되지 않는다 — 순서를 주장하지 않기 때문이다.
     */
    private static void requireNoPrerequisiteCycle(List<ProposedChapter> chapters, List<ProposedRelation> relations) {
        Map<String, List<String>> edges = new HashMap<>();
        for (ProposedRelation relation : relations) {
            if (relation.relationType() == ChapterRelationType.PREREQUISITE) {
                edges.computeIfAbsent(relation.fromKey(), key -> new ArrayList<>())
                        .add(relation.toKey());
            }
        }
        Set<String> visited = new HashSet<>();
        Set<String> onPath = new HashSet<>();
        for (ProposedChapter chapter : chapters) {
            if (hasCycle(chapter.key(), edges, visited, onPath)) {
                throw new IllegalArgumentException("선행 관계에 순환이 있습니다: " + chapter.key());
            }
        }
    }

    /** 반복 DFS. 챕터 수 상한이 40 이라 깊이가 문제되지 않지만, 재귀 대신 스택을 쓰면 상한과 무관해진다. */
    private static boolean hasCycle(
            String start, Map<String, List<String>> edges, Set<String> visited, Set<String> onPath) {
        if (visited.contains(start)) {
            return false;
        }
        Deque<String> stack = new ArrayDeque<>();
        Deque<String> exits = new ArrayDeque<>();
        stack.push(start);

        while (!stack.isEmpty()) {
            String current = stack.peek();
            if (!visited.contains(current)) {
                visited.add(current);
                onPath.add(current);
                exits.push(current);
            }
            boolean descended = false;
            for (String next : edges.getOrDefault(current, List.of())) {
                if (onPath.contains(next)) {
                    return true;
                }
                if (!visited.contains(next)) {
                    stack.push(next);
                    descended = true;
                    break;
                }
            }
            if (!descended) {
                stack.pop();
                if (!exits.isEmpty() && exits.peek().equals(current)) {
                    onPath.remove(exits.pop());
                }
            }
        }
        return false;
    }
}
