package com.mindplates.nextchapter.adapter.out.messaging.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mindplates.nextchapter.application.chapter.port.out.GraphSyncPort;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import com.mindplates.nextchapter.domain.chapter.model.OutboxEventType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 헤더로 분기하고, 모르는 종류는 파티션을 막지 않고 건너뛴다. */
@ExtendWith(MockitoExtension.class)
@DisplayName("outbox 컨슈머")
class ChapterOutboxListenerTest {

    @Mock
    GraphSyncPort graphSyncPort;

    @Test
    @DisplayName("챕터 이벤트는 노드 반영으로 간다")
    void dispatchesChapterEvent() throws Exception {
        new ChapterOutboxListener(graphSyncPort)
                .onOutboxEvent(
                        "{\"skeletonId\":5,\"chapterId\":100,\"chapterKey\":\"loss-function\","
                                + "\"title\":\"손실함수\",\"version\":2}",
                        OutboxEventType.CHAPTER_PERSISTED.name(),
                        "chapter:100:v2");

        verify(graphSyncPort).upsertChapterNode(5L, 100L, "loss-function", "손실함수", 2);
    }

    @Test
    @DisplayName("그래프 이벤트는 관계 교체로 간다")
    void dispatchesGraphEvent() throws Exception {
        new ChapterOutboxListener(graphSyncPort)
                .onOutboxEvent(
                        "{\"skeletonId\":5,\"edges\":[{\"from\":100,\"to\":101,\"type\":\"PREREQUISITE\"},"
                                + "{\"from\":101,\"to\":102,\"type\":\"RELATED\"}]}",
                        OutboxEventType.CHAPTER_GRAPH_PERSISTED.name(),
                        "skeleton:5:graph:2");

        verify(graphSyncPort)
                .replaceRelations(
                        5L,
                        List.of(
                                new GraphSyncPort.Edge(100L, 101L, ChapterRelationType.PREREQUISITE),
                                new GraphSyncPort.Edge(101L, 102L, ChapterRelationType.RELATED)));
    }

    /**
     * 예외를 올리면 그 파티션이 막혀 뒤의 정상 이벤트까지 멈추고, 파티션 키가 뼈대 ID 라 그 뼈대의 반영
     * 전체가 밀린다. 새 버전이 배포되면 오프셋을 되돌려 다시 읽을 수 있다.
     */
    @Test
    @DisplayName("모르는 이벤트 종류는 건너뛴다 — 파티션을 막지 않는다")
    void skipsUnknownEventType() throws Exception {
        new ChapterOutboxListener(graphSyncPort).onOutboxEvent("{}", "SOMETHING_NEW", "k");
        new ChapterOutboxListener(graphSyncPort).onOutboxEvent("{}", null, "k");

        verifyNoInteractions(graphSyncPort);
    }

    @Test
    @DisplayName("모르는 관계 타입은 그 간선만 건너뛴다")
    void skipsUnknownRelationType() throws Exception {
        new ChapterOutboxListener(graphSyncPort)
                .onOutboxEvent(
                        "{\"skeletonId\":5,\"edges\":[{\"from\":100,\"to\":101,\"type\":\"LEADS_TO\"},"
                                + "{\"from\":101,\"to\":102,\"type\":\"RELATED\"}]}",
                        OutboxEventType.CHAPTER_GRAPH_PERSISTED.name(),
                        "k");

        verify(graphSyncPort)
                .replaceRelations(5L, List.of(new GraphSyncPort.Edge(101L, 102L, ChapterRelationType.RELATED)));
        verify(graphSyncPort, never())
                .upsertChapterNode(anyLong(), anyLong(), anyString(), anyString(), any(int.class));
    }
}
