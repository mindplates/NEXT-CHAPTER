package com.mindplates.nextchapter.application.catalog.port.in;

import com.mindplates.nextchapter.application.catalog.port.in.command.SeedCatalogCommand;
import com.mindplates.nextchapter.application.catalog.view.CatalogSeedView;

/**
 * 초기 카탈로그를 채운다. 운영자의 사전 생성이 <b>사용자 경로를 대체하지 않고</b> 보완하는 지점이다.
 *
 * <p><b>뼈대 생성을 트리거하지 않는다.</b> 카탈로그 행만 만든다. 시드 하나가 곧 뼈대 하나이고 뼈대 하나가
 * 전 범위 생성 비용이므로, 기동할 때마다 생성이 걸리면 재기동이 곧 비용이 된다 — 생성은 관리 화면에서
 * 운영자가 시작한다.
 *
 * <p><b>이미 있는 것은 건드리지 않는다.</b> 갱신·삭제 경로가 없는 것이 설계다. 시드 파일을 고쳐 이름을
 * 바꿀 수 있게 하면 운영 중 카탈로그가 재기동으로 덮이고, 그 덮임은 사용자 입력 로그·별칭과 어긋나 조용히
 * 매핑을 틀리게 한다.
 */
public interface SeedCatalogUseCase {

    CatalogSeedView seed(SeedCatalogCommand command);
}
