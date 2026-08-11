package com.mindplates.nextchapter.common.time;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * "사람이 인지하는 시각"의 기준 시간대.
 *
 * <p>JVM 기본 시간대는 UTC 로 고정돼 있다(머신 타임스탬프는 UTC). "오늘"·일일 예산 경계·리포트
 * 같이 사용자·운영자가 인지하는 시각 계산은 전부 {@link #KST} 를 기준으로 한다. 특히 일일 비용
 * 상한은 "하루"의 경계가 어디인지에 따라 값이 달라지므로 기준이 한 곳에 있어야 한다.
 */
public final class AppTime {

    public static final String KST_ZONE_ID = "Asia/Seoul";

    public static final ZoneId KST = ZoneId.of(KST_ZONE_ID);

    private AppTime() {}

    /** 사용자 설정 시간대 문자열을 해석한다. null·공백·무효 문자열이면 {@link #KST} 로 폴백한다. */
    public static ZoneId zoneOrDefault(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return KST;
        }
        try {
            return ZoneId.of(timeZone.trim());
        } catch (DateTimeException e) {
            return KST;
        }
    }
}
