package com.bowe.meetstudent.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class Utils {

    public static LocalDateTime dakarTimeZone() {
        ZoneId dakarZoneId = ZoneId.of("Africa/Dakar");
        return ZonedDateTime.now(dakarZoneId).toLocalDateTime();
    }

}
