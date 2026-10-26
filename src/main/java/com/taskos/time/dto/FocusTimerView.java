package com.taskos.time.dto;

import com.taskos.time.entity.FocusInterval;
import com.taskos.time.entity.FocusSession;
import java.util.List;

public record FocusTimerView(FocusSession session, long remainingSeconds, List<FocusInterval> intervals) { }
