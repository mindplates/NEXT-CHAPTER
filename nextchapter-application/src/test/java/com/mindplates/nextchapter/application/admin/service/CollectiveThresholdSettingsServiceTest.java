package com.mindplates.nextchapter.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.admin.port.out.LoadCollectiveThresholdSettingsPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveCollectiveThresholdSettingsPort;
import com.mindplates.nextchapter.application.admin.view.CollectiveThresholdView;
import com.mindplates.nextchapter.domain.admin.model.CollectiveThresholdSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("집단 루프 임계치 설정")
class CollectiveThresholdSettingsServiceTest {

    @Mock
    LoadCollectiveThresholdSettingsPort loadCollectiveThresholdSettingsPort;

    @Mock
    SaveCollectiveThresholdSettingsPort saveCollectiveThresholdSettingsPort;

    CollectiveThresholdSettingsService service;

    @BeforeEach
    void setUp() {
        service = new CollectiveThresholdSettingsService(
                loadCollectiveThresholdSettingsPort, saveCollectiveThresholdSettingsPort);
        when(loadCollectiveThresholdSettingsPort.load())
                .thenReturn(new CollectiveThresholdSettings(5, 20, 40, null, null, null));
    }

    @Test
    @DisplayName("현재 임계치를 내린다")
    void returnsCurrent() {
        CollectiveThresholdView view = service.current();

        assertThat(view.questionThreshold()).isEqualTo(5);
        assertThat(view.minAttempts()).isEqualTo(20);
        assertThat(view.wrongRatePercent()).isEqualTo(40);
    }

    @Test
    @DisplayName("변경하면 새 값을 저장한다")
    void updatesSettings() {
        when(saveCollectiveThresholdSettingsPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CollectiveThresholdView view = service.update(10, 30, 50, "admin");

        ArgumentCaptor<CollectiveThresholdSettings> captor = ArgumentCaptor.forClass(CollectiveThresholdSettings.class);
        verify(saveCollectiveThresholdSettingsPort).save(captor.capture());
        assertThat(captor.getValue().questionThreshold()).isEqualTo(10);
        assertThat(captor.getValue().minAttempts()).isEqualTo(30);
        assertThat(captor.getValue().wrongRatePercent()).isEqualTo(50);
        assertThat(captor.getValue().updatedBy()).isEqualTo("admin");
        assertThat(view.questionThreshold()).isEqualTo(10);
    }
}
