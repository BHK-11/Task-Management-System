package com.taskos.time.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class LocalTimePolicy {
    private LocalTimePolicy() { }
    public static ZonedDateTime resolve(LocalDateTime local, ZoneId zone) {
        // atZone shifts a nonexistent wall time forward through the DST gap and uses the earlier offset on overlaps.
        return local.atZone(zone);
    }
}
