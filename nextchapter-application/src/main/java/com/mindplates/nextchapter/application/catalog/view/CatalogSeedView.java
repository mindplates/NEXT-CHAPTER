package com.mindplates.nextchapter.application.catalog.view;

/**
 * 시드 결과. <b>새로 만든 것만</b> 센다.
 *
 * <p>"이미 있어서 건너뛴 것"과 "새로 만든 것"을 구분하는 이유는, 재기동마다 같은 숫자가 나오면 시드가
 * 멱등하지 않다는 뜻이기 때문이다 — 로그가 그 사실을 바로 보여 준다.
 */
public record CatalogSeedView(int domainsCreated, int fieldsCreated, int topicsCreated, int aliasesCreated) {

    public boolean createdNothing() {
        return domainsCreated == 0 && fieldsCreated == 0 && topicsCreated == 0 && aliasesCreated == 0;
    }
}
