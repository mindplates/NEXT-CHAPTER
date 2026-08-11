package com.mindplates.nextchapter.adapter.in.web.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.adapter.in.web.support.GlobalExceptionHandler;
import com.mindplates.nextchapter.application.catalog.port.in.CreateCatalogFieldUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.CreateCatalogTopicUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.DeleteCatalogFieldUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.DeleteCatalogTopicUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.GetCatalogUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.ManageTopicAliasUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.UpdateCatalogFieldUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.UpdateCatalogTopicUseCase;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.catalog.model.TopicAlias;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 분야·주제 관리 API.
 *
 * <p>여기서 확인하는 것은 로직이 아니라 <b>배선</b>이다 — 경로가 부모 ID 를 실제로 커맨드에 넣는지,
 * 상태 코드가 맞는지, 응답 봉투가 씌워지는지. 컨트롤러에서 틀리는 것은 대개 이 셋이다.
 */
@WebMvcTest({CatalogFieldAdminController.class, CatalogTopicAdminController.class})
@Import(GlobalExceptionHandler.class)
@DisplayName("분야·주제 관리 API")
class CatalogChildAdminControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetCatalogUseCase getCatalogUseCase;

    @MockitoBean
    CreateCatalogFieldUseCase createCatalogFieldUseCase;

    @MockitoBean
    UpdateCatalogFieldUseCase updateCatalogFieldUseCase;

    @MockitoBean
    DeleteCatalogFieldUseCase deleteCatalogFieldUseCase;

    @MockitoBean
    CreateCatalogTopicUseCase createCatalogTopicUseCase;

    @MockitoBean
    UpdateCatalogTopicUseCase updateCatalogTopicUseCase;

    @MockitoBean
    DeleteCatalogTopicUseCase deleteCatalogTopicUseCase;

    @MockitoBean
    ManageTopicAliasUseCase manageTopicAliasUseCase;

    @Test
    @DisplayName("분야 목록은 경로의 도메인 ID 로 조회된다")
    void listsFieldsByDomain() throws Exception {
        when(getCatalogUseCase.fields(1L)).thenReturn(List.of(field()));

        mockMvc.perform(get("/api/admin/catalog/domains/1/fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slug").value("ai"))
                .andExpect(jsonPath("$.data[0].domainId").value(1));
    }

    @Test
    @DisplayName("분야 생성은 경로의 도메인 ID 를 커맨드에 넣는다")
    void createFieldUsesPathDomainId() throws Exception {
        when(createCatalogFieldUseCase.create(any())).thenReturn(field());

        mockMvc.perform(post("/api/admin/catalog/domains/1/fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"ai\",\"name\":\"인공지능\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.domainId").value(1));

        verify(createCatalogFieldUseCase)
                .create(org.mockito.ArgumentMatchers.argThat(command -> command.domainId() == 1L));
    }

    @Test
    @DisplayName("분야 수정·삭제·단건 조회가 연결돼 있다")
    void fieldCrudIsWired() throws Exception {
        when(getCatalogUseCase.field(10L)).thenReturn(field());
        when(updateCatalogFieldUseCase.update(eq(10L), any())).thenReturn(field());

        mockMvc.perform(get("/api/admin/catalog/fields/10")).andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/catalog/fields/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"인공지능\",\"sortOrder\":3}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/catalog/fields/10")).andExpect(status().isOk());

        verify(deleteCatalogFieldUseCase).delete(10L);
    }

    @Test
    @DisplayName("주제 생성은 경로의 분야 ID 를 커맨드에 넣는다")
    void createTopicUsesPathFieldId() throws Exception {
        when(createCatalogTopicUseCase.create(any())).thenReturn(topic());

        mockMvc.perform(post("/api/admin/catalog/fields/10/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"machine-learning\",\"name\":\"머신러닝\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fieldId").value(10));

        verify(createCatalogTopicUseCase)
                .create(org.mockito.ArgumentMatchers.argThat(command -> command.fieldId() == 10L));
    }

    @Test
    @DisplayName("주제 수정·삭제·단건 조회·목록이 연결돼 있다")
    void topicCrudIsWired() throws Exception {
        when(getCatalogUseCase.topic(100L)).thenReturn(topic());
        when(getCatalogUseCase.topics(10L)).thenReturn(List.of(topic()));
        when(updateCatalogTopicUseCase.update(eq(100L), any())).thenReturn(topic());

        mockMvc.perform(get("/api/admin/catalog/fields/10/topics")).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/catalog/topics/100")).andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/catalog/topics/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"머신러닝\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/catalog/topics/100")).andExpect(status().isOk());

        verify(deleteCatalogTopicUseCase).delete(100L);
    }

    @Test
    @DisplayName("별칭 추가·조회·삭제가 연결돼 있다")
    void aliasEndpointsAreWired() throws Exception {
        when(getCatalogUseCase.aliases(100L)).thenReturn(List.of(new TopicAlias(9L, 100L, "기계학습", null)));
        when(manageTopicAliasUseCase.add(100L, "기계학습")).thenReturn(new TopicAlias(9L, 100L, "기계학습", null));

        mockMvc.perform(get("/api/admin/catalog/topics/100/aliases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].alias").value("기계학습"))
                // 정규화 값은 파생값이므로 응답에서도 계산돼 나간다.
                .andExpect(jsonPath("$.data[0].normalized").value("기계학습"));

        mockMvc.perform(post("/api/admin/catalog/topics/100/aliases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alias\":\"기계학습\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/admin/catalog/aliases/9")).andExpect(status().isOk());
        verify(manageTopicAliasUseCase).remove(9L);
    }

    @Test
    @DisplayName("없는 부모는 404 다")
    void missingParentIsNotFound() throws Exception {
        when(createCatalogTopicUseCase.create(any())).thenThrow(new EntityNotFoundException("분야", 99L));

        mockMvc.perform(post("/api/admin/catalog/fields/99/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"ml\",\"name\":\"머신러닝\"}"))
                .andExpect(status().isNotFound());
    }

    private static CatalogField field() {
        return new CatalogField(10L, 1L, "ai", "인공지능", null, 0, null, null);
    }

    private static CatalogTopic topic() {
        return new CatalogTopic(100L, 10L, "machine-learning", "머신러닝", null, 0, null, null);
    }
}
