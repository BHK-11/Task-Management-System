package com.taskos.goals.dto;

import com.taskos.goals.entity.KeyResult;
import com.taskos.goals.entity.KeyResultMeasurement;
import java.math.BigDecimal;
import java.util.List;

public record KeyResultView(KeyResult result, BigDecimal currentValue, BigDecimal progress, int displayPercent,
        List<KeyResultMeasurement> history) { }
