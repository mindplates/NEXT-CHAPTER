package com.mindplates.nextchapter.adapter.in.web.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindplates.nextchapter.adapter.in.web.support.GlobalExceptionHandler;
import com.mindplates.nextchapter.application.catalog.port.in.CreateCatalogDomainUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.DeleteCatalogDomainUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.GetCatalogUseCase;
import com.mindplates.nextchapter.application.catalog.port.in.UpdateCatalogDomainUseCase;
import com.mindplates.nextchapter.common.exception.DuplicateException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CatalogDomainAdminController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("도메인 관리 API")
class CatalogDomainAdminControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetCatalogUseCase getCatalogUseCase;

    @MockitoBean
    CreateCatalogDomainUseCase createCatalogDomainUseCase;

    @MockitoBean
    UpdateCatalogDomainUseCase updateCatalogDomainUseCase;

    @MockitoBean
    DeleteCatalogDomainUseCase deleteCatalogDomainUseCase;

    @Test
    @DisplayName("목록은 ApiResponse 의 data 로 내려간다")
    void listWrapsInApiResponse() throws Exception {
        when(getCatalogUseCase.domains()).thenReturn(List.of(domain()));

        mockMvc.perform(get("/api/admin/catalog/domains"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slug").value("computer-science"))
                .andExpect(jsonPath("$.data[0].name").value("컴퓨터과학"))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    @DisplayName("생성은 201 을 반환한다")
    void createReturnsCreated() throws Exception {
        when(createCatalogDomainUseCase.create(any())).thenReturn(domain());

        mockMvc.perform(post("/api/admin/catalog/domains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"computer-science\",\"name\":\"컴퓨터과학\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("필수 필드가 없으면 400 과 필드 오류를 준다")
    void validationFailureReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/admin/catalog/domains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"컴퓨터과학\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("slug"));
    }

    @Test
    @DisplayName("slug 중복은 409 다")
    void duplicateSlugReturnsConflict() throws Exception {
        when(createCatalogDomainUseCase.create(any()))
                .thenThrow(new DuplicateException("도메인", "slug", "computer-science"));

        mockMvc.perform(post("/api/admin/catalog/domains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"computer-science\",\"name\":\"컴퓨터과학\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("slug")));
    }

    @Test
    @DisplayName("자식이 남은 도메인 삭제는 400 과 사유를 준다")
    void deleteWithChildrenReturnsBadRequest() throws Exception {
        Mockito.doThrow(new InvalidOperationException("분야가 2개 남아 있어 도메인을 삭제할 수 없습니다."))
                .when(deleteCatalogDomainUseCase)
                .delete(1L);

        mockMvc.perform(delete("/api/admin/catalog/domains/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("2개")));
    }

    private static CatalogDomain domain() {
        return new CatalogDomain(1L, "computer-science", "컴퓨터과학", "설명", 0, null, null);
    }
}
