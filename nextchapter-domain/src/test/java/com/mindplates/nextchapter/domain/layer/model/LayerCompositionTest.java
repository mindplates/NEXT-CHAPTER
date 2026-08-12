package com.mindplates.nextchapter.domain.layer.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockDocument;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 합성이 지켜야 하는 것은 <b>본문이 바뀌지 않는다</b>는 사실이다. 본문을 사용자마다 다시 쓰면 여러 사람이
 * 각자 다른 텍스트를 본 것이 되어 "같은 지점에서 틀렸다"를 비교할 수 없고, 그 비교가 집단 루프의 전부다.
 */
@DisplayName("레이어 합성")
class LayerCompositionTest {

    private static BlockDocument document() {
        return BlockDocument.of(
                Block.text("b1", BlockType.HEADING, "경사하강법이란"),
                Block.text("b2", BlockType.PARAGRAPH, "손실함수를 줄이는 방향으로"),
                Block.text("b3", BlockType.NARRATION, "이제 학습률을 봅시다"),
                Block.text("b4", BlockType.FORMULA, "\\theta := \\theta - \\alpha \\nabla J"));
    }

    private static SupplementBlock supplement(String anchor, String id, String text) {
        return new SupplementBlock(anchor, Block.text(id, BlockType.PARAGRAPH, text));
    }

    @Test
    @DisplayName("레이어가 비면 합성은 항등이다 — 처음 들어온 사용자가 정상 상태다")
    void emptyLayerIsIdentity() {
        List<Block> composed = LayerComposition.compose(document(), DeliveryFormat.WEB, ChapterLayer.empty(42L, 100L));

        assertThat(composed).extracting(Block::id).containsExactly("b1", "b2", "b4");
    }

    @Test
    @DisplayName("null 레이어도 항등이다")
    void nullLayerIsIdentity() {
        assertThat(LayerComposition.compose(document(), DeliveryFormat.WEB, null))
                .extracting(Block::id)
                .containsExactly("b1", "b2", "b4");
    }

    @Test
    @DisplayName("보충 블록은 앵커 바로 뒤에 온다")
    void insertsAfterAnchor() {
        ChapterLayer layer = new ChapterLayer(42L, 100L, List.of(supplement("b2", "s1", "예를 들어")));

        List<Block> composed = LayerComposition.compose(document(), DeliveryFormat.WEB, layer);

        assertThat(composed).extracting(Block::id).containsExactly("b1", "b2", "s1", "b4");
    }

    @Test
    @DisplayName("같은 앵커에 여러 개면 준 순서를 지킨다")
    void keepsSupplementOrder() {
        ChapterLayer layer =
                new ChapterLayer(42L, 100L, List.of(supplement("b2", "s1", "먼저"), supplement("b2", "s2", "다음")));

        assertThat(LayerComposition.compose(document(), DeliveryFormat.WEB, layer))
                .extracting(Block::id)
                .containsExactly("b1", "b2", "s1", "s2", "b4");
    }

    /** 본문 블록이 바뀌거나 사라지면 집단 신호를 겹쳐 볼 수 없다. 구조로 막혀 있음을 고정해 둔다. */
    @Test
    @DisplayName("본문 블록의 내용과 순서는 그대로다")
    void bodyBlocksAreUntouched() {
        ChapterLayer layer = new ChapterLayer(42L, 100L, List.of(supplement("b1", "s1", "추가")));

        List<Block> composed = LayerComposition.compose(document(), DeliveryFormat.WEB, layer);

        assertThat(composed)
                .filteredOn(block -> block.id().startsWith("b"))
                .containsExactlyElementsOf(document().webBlocks());
    }

    /**
     * 앵커가 사라졌다는 것은 그 문단이 수정으로 없어졌다는 뜻이다. 끝에 몰아 붙이면 맥락 없는 곳에
     * 설명이 나타난다.
     */
    @Test
    @DisplayName("앵커가 없는 보충 블록은 버린다")
    void dropsOrphanSupplements() {
        ChapterLayer layer = new ChapterLayer(42L, 100L, List.of(supplement("b9", "s1", "사라진 문단 설명")));

        assertThat(LayerComposition.compose(document(), DeliveryFormat.WEB, layer))
                .extracting(Block::id)
                .containsExactly("b1", "b2", "b4");
    }

    /** narration 앵커에 붙은 보충 블록은 웹에서 앵커째 걸러지므로 함께 사라진다 — 앵커 없는 삽입은 없다. */
    @Test
    @DisplayName("형태에서 걸러진 블록에 붙은 보충 블록도 나오지 않는다")
    void dropsSupplementsOfFilteredAnchor() {
        ChapterLayer layer = new ChapterLayer(42L, 100L, List.of(supplement("b3", "s1", "대본 보충")));

        assertThat(LayerComposition.compose(document(), DeliveryFormat.WEB, layer))
                .extracting(Block::id)
                .containsExactly("b1", "b2", "b4");
        assertThat(LayerComposition.compose(document(), DeliveryFormat.VIDEO, layer))
                .extracting(Block::id)
                .containsExactly("b1", "b2", "b3", "s1", "b4");
    }

    @Test
    @DisplayName("웹만 narration 을 걸러 낸다")
    void onlyWebFiltersNarration() {
        assertThat(LayerComposition.compose(document(), DeliveryFormat.PRESENTATION, null))
                .extracting(Block::id)
                .containsExactly("b1", "b2", "b3", "b4");
    }

    /** 앵커가 보충 ID 면 보충 블록에 보충을 붙이는 것이 되고, 그 순서는 정의되지 않는다. */
    @Test
    @DisplayName("앵커는 공용 본문 ID 여야 한다")
    void anchorMustBeBodyId() {
        Block block = Block.text("s2", BlockType.PARAGRAPH, "추가");

        assertThatThrownBy(() -> new SupplementBlock("s1", block)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SupplementBlock(null, block)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 보충 블록이 공용 ID 를 쓰면 개인화 블록의 신호가 공용 블록 신호와 섞인다. */
    @Test
    @DisplayName("보충 블록 ID 는 보충 공간이어야 한다")
    void supplementMustUseSupplementId() {
        Block bodyId = Block.text("b9", BlockType.PARAGRAPH, "추가");

        assertThatThrownBy(() -> new SupplementBlock("b1", bodyId)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SupplementBlock("b1", null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 공용 문서에 보충 ID 가 들어오면 저장 시점에 막아야 한다 — 통과하면 집계가 조용히 오염된다. */
    @Test
    @DisplayName("공용 블록 문서는 보충 ID 를 담을 수 없다")
    void documentRejectsSupplementIds() {
        assertThatThrownBy(() -> BlockDocument.of(Block.text("s1", BlockType.PARAGRAPH, "본문")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("본문 ID");
    }

    @Test
    @DisplayName("레이어는 자기 소유자와 챕터를 안다 — 챕터 ID 기준이라 버전이 올라도 유지된다")
    void layerKnowsOwner() {
        ChapterLayer layer = ChapterLayer.empty(42L, 100L);

        assertThat(layer.userId()).isEqualTo(42L);
        assertThat(layer.chapterId()).isEqualTo(100L);
        assertThat(layer.isEmpty()).isTrue();
        assertThat(new ChapterLayer(42L, 100L, null).supplements()).isEmpty();
    }
}
