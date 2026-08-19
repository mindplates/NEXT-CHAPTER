package com.mindplates.nextchapter.domain.signal.model;

import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;

/**
 * 블록 하나의 신호를 딜리버리 형태별로 나눈 값. 임계치 판정에는 쓰지 않고, 수정안의 근거로만 쓴다.
 *
 * <p>"이 지점은 웹에서만 오답률이 높다" 같은 진단이 이 값으로 가능해진다 — 통합 집계만으로는 형태가
 * 결함의 원인인지 알 수 없다.
 */
public record FormatBreakdown(DeliveryFormat format, long attemptCount, long wrongCount) {

    public FormatBreakdown {
        if (format == null) {
            throw new IllegalArgumentException("딜리버리 형태는 필수입니다.");
        }
    }

    public double wrongRate() {
        return attemptCount == 0 ? 0.0 : (double) wrongCount / attemptCount;
    }
}
