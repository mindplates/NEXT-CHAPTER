package com.mindplates.nextchapter.application.catalog.view;

/**
 * 임베딩 현황. 관리 화면이 "모델을 바꿨는데 재임베딩이 끝났는가"를 볼 수 있어야 한다.
 *
 * @param model 지금 임베딩 단계에 설정된 모델
 * @param embedded 그 모델로 만들어진 벡터를 가진 주제 수
 * @param outdated 벡터가 없거나 다른 모델로 만들어진 주제 수
 */
public record EmbeddingStatusView(String model, long embedded, long outdated) {

    public boolean isUpToDate() {
        return outdated == 0;
    }
}
