package com.taskos.automation.service;

import com.taskos.automation.entity.AutomationRuleStatus;
import com.taskos.automation.repository.AutomationRuleRepository;
import com.taskos.automation.repository.AutomationRuleVersionRepository;
import com.taskos.automation.repository.AutomationScheduleRepository;
import java.time.Clock;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationScheduleService {
    private final AutomationScheduleRepository schedules;
    private final AutomationRuleRepository rules;
    private final AutomationRuleVersionRepository versions;
    private final AutomationRuleService ruleService;
    private final AutomationScheduleCalculator calculator;
    private final AutomationOutboxService outbox;
    private final Clock clock;

    public AutomationScheduleService(AutomationScheduleRepository schedules, AutomationRuleRepository rules,
            AutomationRuleVersionRepository versions, AutomationRuleService ruleService,
            AutomationScheduleCalculator calculator, AutomationOutboxService outbox, Clock clock) {
        this.schedules = schedules; this.rules = rules; this.versions = versions; this.ruleService = ruleService;
        this.calculator = calculator; this.outbox = outbox; this.clock = clock;
    }

    @Transactional
    public int fireDue() {
        var now = clock.instant(); int fired = 0;
        var due = schedules.findAllByActiveTrueAndNextFireAtLessThanEqualOrderByNextFireAtAsc(now, PageRequest.of(0, 50));
        for (var schedule : due) {
            var rule = rules.findById(schedule.getRuleId()).orElse(null);
            if (rule == null || rule.getStatus() != AutomationRuleStatus.ENABLED) { schedule.deactivate(); continue; }
            var version = versions.findByIdAndRuleId(schedule.getRuleVersionId(), rule.getId()).orElse(null);
            if (version == null) { schedule.deactivate(); continue; }
            var definition = ruleService.read(version.getDefinitionJson());
            var scheduledAt = schedule.getNextFireAt();
            boolean missed = calculator.isMissedByMoreThanOneInterval(definition.schedule(), scheduledAt, now);
            if (missed && schedule.getMisfirePolicy() == com.taskos.automation.entity.AutomationMisfirePolicy.SKIP_MISSED) {
                schedule.fire(scheduledAt, calculator.next(definition.schedule(), now));
                continue;
            }
            outbox.publishScheduledEvent(rule.getScopeType(), rule.getScopeId(), rule.getCreatedById(),
                    Map.of("scheduledFor", scheduledAt.toString(), "ruleName", rule.getName()));
            var nextFrom = missed ? now : scheduledAt;
            schedule.fire(scheduledAt, calculator.next(definition.schedule(), nextFrom));
            fired++;
        }
        return fired;
    }
}
