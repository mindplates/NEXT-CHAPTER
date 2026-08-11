package com.mindplates.nextchapter.adapter.out.messaging.generation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 생성 파이프라인 토픽 이름과 파티션 수. <b>토픽 네이밍·파티션 전략의 확정 지점.</b>
 *
 * <h2>네이밍</h2>
 *
 * {@code {집합체}.{단계}.{이벤트}} — 소문자, 점 구분. 예: {@code skeleton.body.requested}.
 *
 * <p>집합체를 앞에 두는 이유는 토픽 목록이 알파벳순으로 정렬될 때 같은 집합체가 모이기 때문이다.
 * 이벤트를 과거분사로 두는 이유는 토픽이 "일어난 일"을 담기 때문이다 — 명령형({@code create-body})으로
 * 두면 컨슈머가 하나뿐이라는 가정이 이름에 박힌다.
 *
 * <p>{@code prefix} 는 클러스터를 공유할 때만 쓴다. 기본값은 빈 문자열이고, 이름을 설정으로 뺀 이유는
 * 개발·스테이징이 한 클러스터를 쓰는 상황에서 토픽이 섞이면 한쪽의 재처리가 다른 쪽 데이터를 만든다.
 *
 * <h2>파티션</h2>
 *
 * <table>
 *   <tr><th>토픽</th><th>키</th><th>파티션</th><th>이유</th></tr>
 *   <tr><td>graph.requested</td><td>뼈대 ID</td><td>3</td><td>뼈대당 1건. 동시 생성 뼈대 수가 병렬도다</td></tr>
 *   <tr><td>outline.requested</td><td>뼈대 ID</td><td>3</td><td>같음</td></tr>
 *   <tr><td>body.requested</td><td><b>챕터 ID</b></td><td>12</td><td>챕터당 1건. 여기가 실제 병렬 구간이다</td></tr>
 * </table>
 *
 * <p><b>본문 토픽의 키가 챕터 ID 인 것이 이 표의 핵심이다.</b> 뼈대 ID 를 키로 쓰면 한 뼈대의 챕터가
 * 전부 같은 파티션에 몰려 병렬도가 1이 된다 — 챕터가 30개인 뼈대의 본문 생성이 순차로 돌고, 파티션을
 * 늘려도 아무 효과가 없다. 그 실패는 에러 없이 "그냥 느린 것"으로 보인다.
 *
 * <p>키를 아예 비우면 라운드로빈으로 흩어져 병렬도는 나오지만 같은 챕터의 재시도가 다른 파티션에 갈 수
 * 있다. 챕터 ID 를 키로 두면 병렬도와 챕터별 순서를 동시에 얻는다.
 *
 * @param bodyPartitions 본문 생성의 병렬도 상한. 컨슈머 concurrency 를 이 값 이상으로 올려도 소용없다
 */
@ConfigurationProperties(prefix = "nextchapter.kafka")
public record GenerationTopicProperties(
        String prefix, int graphPartitions, int outlinePartitions, int bodyPartitions, short replicationFactor) {

    private static final String GRAPH_REQUESTED = "skeleton.graph.requested";
    private static final String OUTLINE_REQUESTED = "skeleton.outline.requested";
    private static final String BODY_REQUESTED = "skeleton.body.requested";

    public GenerationTopicProperties {
        prefix = prefix == null ? "" : prefix.trim();
        graphPartitions = graphPartitions < 1 ? 3 : graphPartitions;
        outlinePartitions = outlinePartitions < 1 ? 3 : outlinePartitions;
        bodyPartitions = bodyPartitions < 1 ? 12 : bodyPartitions;
        replicationFactor = replicationFactor < 1 ? (short) 1 : replicationFactor;
    }

    public String graphRequested() {
        return prefix + GRAPH_REQUESTED;
    }

    public String outlineRequested() {
        return prefix + OUTLINE_REQUESTED;
    }

    public String bodyRequested() {
        return prefix + BODY_REQUESTED;
    }
}
