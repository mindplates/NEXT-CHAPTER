package com.mindplates.nextchapter.domain.signal.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("수정 트리거")
class RevisionTriggerTest {

    @Test
    @DisplayName("집계에서 만들어진다")
    void createsFromAggregate() {
        BlockSignalAggregate aggregate = new BlockSignalAggregate(100L, 2, "b6", 5, 20, 9);

        RevisionTrigger trigger = RevisionTrigger.of(aggregate);

        assertThat(trigger.chapterId()).isEqualTo(100L);
        assertThat(trigger.chapterVersion()).isEqualTo(2);
        assertThat(trigger.blockId()).isEqualTo("b6");
        assertThat(trigger.questionCount()).isEqualTo(5);
        assertThat(trigger.attemptCount()).isEqualTo(20);
        assertThat(trigger.wrongCount()).isEqualTo(9);
        assertThat(trigger.triggeredAt()).isNotNull();
    }

    @Test
    @DisplayName("보충 블록에는 붙지 않는다")
    void rejectsSupplementBlock() {
        BlockSignalAggregate aggregate = new BlockSignalAggregate(100L, 2, "s1", 5, 0, 0);

        assertThatThrownBy(() -> RevisionTrigger.of(aggregate)).isInstanceOf(IllegalArgumentException.class);
    }
}
