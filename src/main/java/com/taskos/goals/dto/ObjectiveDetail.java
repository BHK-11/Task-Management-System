package com.taskos.goals.dto;

import com.taskos.goals.entity.Objective;
import com.taskos.goals.entity.OkrCycle;
import java.math.BigDecimal;
import java.util.List;

public record ObjectiveDetail(Objective objective, OkrCycle cycle, List<KeyResultView> keyResults,
        BigDecimal progress, int displayPercent, long measuredCount) { }
