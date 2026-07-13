package com.riskcontrol.offline;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

final class DateUtil {
    private static final ThreadLocal<SimpleDateFormat> DT_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
            format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            return format;
        }
    };

    private DateUtil() {
    }

    static String dtFromMillis(long millis) {
        if (millis <= 0L) {
            return "";
        }
        return DT_FORMAT.get().format(new Date(millis));
    }
}
