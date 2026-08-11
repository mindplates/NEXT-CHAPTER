package com.mindplates.nextchapter.adapter.out.persistence.chapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockDocument;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 블록 문서 ↔ jsonb 문자열.
 *
 * <p>도메인 레코드를 직접 직렬화하지 않고 별도 DTO 를 지나는 이유는 저장 형식을 도메인 변경으로부터
 * 떼어 놓기 위해서다. 도메인 레코드를 그대로 쓰면 필드 이름을 바꾸는 리팩터링이 <b>이미 저장된 수천
 * 개의 문서를 읽을 수 없게</b> 만들고, 그 실패는 배포 후에야 드러난다.
 *
 * <p>읽을 때 도메인 검증을 다시 통과시킨다. 저장된 문서가 규칙을 위반한 상태(예: ID 중복)면 조용히
 * 흘려보내는 대신 여기서 멈춘다 — 중복 ID 는 신호를 두 지점에 겹쳐 붙이므로 조용히 통과하는 것이 가장
 * 나쁜 결과다.
 */
@Component
public class BlockDocumentJsonMapper {

    private static final TypeReference<List<BlockJson>> BLOCK_LIST = new TypeReference<>() {};

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String toJson(BlockDocument document) {
        try {
            List<BlockJson> blocks = document.blocks().stream()
                    .map(block -> new BlockJson(block.id(), block.type().name(), block.text(), block.attributes()))
                    .toList();
            return objectMapper.writeValueAsString(blocks);
        } catch (Exception e) {
            throw new InvalidOperationException(
                    "블록 문서를 저장 형식으로 변환하지 못했습니다: " + e.getClass().getSimpleName());
        }
    }

    public BlockDocument toDocument(String json) {
        try {
            List<Block> blocks = objectMapper.readValue(json, BLOCK_LIST).stream()
                    .map(BlockJson::toDomain)
                    .toList();
            return new BlockDocument(blocks);
        } catch (IllegalArgumentException e) {
            // 도메인 검증 실패는 그대로 올린다 — 저장된 문서가 규칙을 위반했다는 뜻이다.
            throw e;
        } catch (Exception e) {
            throw new InvalidOperationException(
                    "저장된 블록 문서를 읽지 못했습니다: " + e.getClass().getSimpleName());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BlockJson(String id, String type, String text, Map<String, Object> attributes) {

        Block toDomain() {
            return new Block(id, BlockType.valueOf(type), text, attributes);
        }
    }
}
