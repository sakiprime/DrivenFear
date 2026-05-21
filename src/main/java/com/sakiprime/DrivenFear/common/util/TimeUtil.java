package com.sakiprime.DrivenFear.common.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TimeUtil {

    private static final DateTimeFormatter SECOND_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.of("Asia/Shanghai"));//时区
    private static final DateTimeFormatter DAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    .withZone(ZoneId.of("Asia/Shanghai"));
    private static final DateTimeFormatter MONTH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM")
                    .withZone(ZoneId.of("Asia/Shanghai"));
    private static final DateTimeFormatter ONLY_DAY_FORMATTER =
            DateTimeFormatter.ofPattern("dd")
                    .withZone(ZoneId.of("Asia/Shanghai"));

    public static String nowSecond() {
        return LocalDateTime.now().format(SECOND_FORMATTER);
    }

    public static String nowDay() {
        return LocalDateTime.now().format(DAY_FORMATTER);
    }
    public static String nowOnlyDay() {
        return LocalDateTime.now().format(ONLY_DAY_FORMATTER);
    }

    public static String nowMonth() {
        return LocalDateTime.now().format(MONTH_FORMATTER);
    }
    public static long nowSecondTimestamp() {
        return System.currentTimeMillis() / 1000;
    }
}