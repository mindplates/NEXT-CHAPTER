package com.mindplates.nextchapter.application.catalog.port.in;

import com.mindplates.nextchapter.application.catalog.view.CatalogTreeView;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.catalog.model.TopicAlias;
import java.util.List;

public interface GetCatalogUseCase {

    List<CatalogDomain> domains();

    CatalogDomain domain(Long id);

    List<CatalogField> fields(Long domainId);

    CatalogField field(Long id);

    List<CatalogTopic> topics(Long fieldId);

    CatalogTopic topic(Long id);

    List<TopicAlias> aliases(Long topicId);

    /**
     * 카탈로그 전체를 한 번에 내린다. 매핑 프롬프트가 층별 후보 목록을 필요로 하고, 관리 화면도
     * 트리로 보여준다 — 세 계층을 각각 왕복하면 화면 하나에 요청이 수십 번 붙는다.
     */
    CatalogTreeView tree();
}
