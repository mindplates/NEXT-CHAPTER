package com.mindplates.nextchapter.domain.layer.model;

import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockDocument;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 합성 — {@code 공용 블록 문서 + 사용자 레이어} → 개인화된 블록 목록.
 *
 * <p><b>합성은 레이어 병합이지 렌더가 아니다.</b> 결과는 여전히 블록 목록이고, HTML 로 바꾸는 것은
 * 클라이언트의 몫이다. 서버가 렌더까지 하면 모바일·태블릿 앱을 붙일 때 API 를 다시 만들어야 한다.
 *
 * <p>이 함수가 <b>할 수 없는 일</b>이 설계의 핵심이다. 본문 블록을 바꾸거나 지우는 경로가 없다 —
 * 입력의 {@link Block} 인스턴스를 그대로 새 목록에 옮기고 사이에 보충 블록을 끼우는 것뿐이다. 규칙으로
 * 두면 언젠가 깨지므로 <b>구조로</b> 막는다. 본문이 사용자마다 달라지는 순간 "같은 지점에서 틀렸다"를
 * 비교할 수 없고, 그러면 집단 루프가 작동을 멈춘다.
 */
public final class LayerComposition {

    private LayerComposition() {}

    /**
     * @param format 형태별로 쓰지 않는 블록을 걸러 낸다 (웹은 narration 을 그리지 않는다)
     * @return 원본 순서를 유지한 블록 목록. 각 보충 블록은 자기 앵커 <b>바로 뒤</b>에 온다
     */
    public static List<Block> compose(BlockDocument document, DeliveryFormat format, ChapterLayer layer) {
        return compose(document, format, layer, UnderstandingLevel.UNKNOWN);
    }

    /**
     * 이해도까지 반영한 합성.
     *
     * <p>이해도가 바꾸는 것은 <b>퀴즈 문항의 선택</b>뿐이다. 설명 블록은 누구에게나 같은 텍스트여야 하고,
     * 그래야 "같은 지점에서 틀렸다"를 겹쳐 볼 수 있다. 문항을 고르는 것은 그 비교를 깨지 않는다 — 각 문항의
     * 오답률은 <b>그 문항을 본 사람들 사이에서</b> 계산되고, 신호에 난이도가 함께 기록돼 집계에서 보정할 수
     * 있다.
     */
    public static List<Block> compose(
            BlockDocument document, DeliveryFormat format, ChapterLayer layer, UnderstandingLevel level) {
        List<Block> base = document.blocksFor(format).stream()
                .filter(block -> QuizSelection.includes(level, block))
                .toList();
        if (layer == null || layer.isEmpty()) {
            return base;
        }

        Map<String, List<Block>> byAnchor = new LinkedHashMap<>();
        for (SupplementBlock supplement : layer.supplements()) {
            byAnchor.computeIfAbsent(supplement.anchorBlockId(), key -> new ArrayList<>())
                    .add(supplement.block());
        }

        List<Block> composed = new ArrayList<>(base.size() + layer.supplements().size());
        for (Block block : base) {
            composed.add(block);
            // 앵커가 없는 보충 블록은 버린다. 앵커가 사라졌다는 것은 그 문단이 수정으로 없어졌다는
            // 뜻이고, 그러면 그 설명이 붙을 자리도 없다. 끝에 몰아 붙이면 맥락 없는 곳에 나타난다.
            composed.addAll(byAnchor.getOrDefault(block.id(), List.of()));
        }
        return List.copyOf(composed);
    }
}
