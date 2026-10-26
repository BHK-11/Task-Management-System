package com.taskos.goals.service;

import com.taskos.goals.dto.KeyResultForm;
import com.taskos.goals.dto.KeyResultMeasurementForm;
import com.taskos.goals.dto.KeyResultView;
import com.taskos.goals.dto.ObjectiveDetail;
import com.taskos.goals.dto.OkrForm;
import com.taskos.goals.entity.KeyResult;
import com.taskos.goals.entity.KeyResultMeasurement;
import com.taskos.goals.entity.Objective;
import com.taskos.goals.entity.OkrCycle;
import com.taskos.goals.repository.GoalRepository;
import com.taskos.goals.repository.KeyResultMeasurementRepository;
import com.taskos.goals.repository.KeyResultRepository;
import com.taskos.goals.repository.ObjectiveRepository;
import com.taskos.goals.repository.OkrCycleRepository;
import com.taskos.time.service.TimeZoneResolver;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OkrService {
    private final OkrCycleRepository cycles;
    private final ObjectiveRepository objectives;
    private final KeyResultRepository results;
    private final KeyResultMeasurementRepository measurements;
    private final GoalRepository goals;
    private final Clock clock;
    private final TimeZoneResolver zones;

    public OkrService(OkrCycleRepository cycles, ObjectiveRepository objectives, KeyResultRepository results,
            KeyResultMeasurementRepository measurements, GoalRepository goals, Clock clock, TimeZoneResolver zones) {
        this.cycles = cycles; this.objectives = objectives; this.results = results; this.measurements = measurements; this.goals = goals; this.clock = clock; this.zones = zones;
    }

    @Transactional(readOnly = true) public List<OkrCycle> cycles(UUID ownerId) { return cycles.findAllByOwnerIdOrderByStartDateDesc(ownerId); }
    @Transactional(readOnly = true) public List<Objective> objectives(UUID ownerId, UUID cycleId) { ownCycle(ownerId, cycleId); return objectives.findAllByOwnerIdAndCycleIdOrderByCreatedAtAsc(ownerId, cycleId); }

    @Transactional
    public UUID createObjective(UUID ownerId, OkrForm form) {
        if (form.getCycleName() == null || form.getCycleName().isBlank() || form.getCycleName().strip().length() > 100)
            throw new IllegalArgumentException("Enter a review cycle name up to 100 characters.");
        if (form.getCycleStart() == null || form.getCycleEnd() == null || form.getCycleEnd().isBefore(form.getCycleStart()))
            throw new IllegalArgumentException("The review cycle end must be on or after its start.");
        if (form.getTitle() == null || form.getTitle().isBlank() || form.getTitle().strip().length() > 160 || (form.getDescription() != null && form.getDescription().length() > 1200))
            throw new IllegalArgumentException("Enter an objective title up to 160 characters and description up to 1,200 characters.");
        if (form.getLinkedGoalId() != null && goals.findByIdAndOwnerId(form.getLinkedGoalId(), ownerId).isEmpty())
            throw new IllegalArgumentException("Choose one of your own goals.");
        String name = form.getCycleName().strip();
        OkrCycle cycle = cycles.findByOwnerIdAndNameAndStartDateAndEndDate(ownerId, name, form.getCycleStart(), form.getCycleEnd())
                .orElseGet(() -> cycles.save(OkrCycle.create(ownerId, name, form.getCycleStart(), form.getCycleEnd(), clock.instant())));
        return objectives.save(Objective.create(ownerId, cycle.getId(), form.getLinkedGoalId(), form.getTitle(), form.getDescription(), clock.instant())).getId();
    }

    @Transactional
    public UUID addKeyResult(UUID ownerId, UUID objectiveId, KeyResultForm form) {
        Objective objective = ownObjective(ownerId, objectiveId);
        if (form.getTitle() == null || form.getTitle().isBlank() || form.getTitle().strip().length() > 160 ||
                form.getBaseline() == null || form.getTarget() == null || form.getBaseline().compareTo(form.getTarget()) == 0)
            throw new IllegalArgumentException("Enter a key result with different baseline and target values.");
        String direction = form.getDirection();
        if (!"INCREASE".equals(direction) && !"DECREASE".equals(direction)) throw new IllegalArgumentException("Choose increasing or decreasing progress.");
        if (("INCREASE".equals(direction) && form.getTarget().compareTo(form.getBaseline()) < 0) ||
                ("DECREASE".equals(direction) && form.getTarget().compareTo(form.getBaseline()) > 0))
            throw new IllegalArgumentException("The target direction must match the selected progress direction.");
        if (form.getUnit() != null && form.getUnit().length() > 40) throw new IllegalArgumentException("Use a measurement unit up to 40 characters.");
        return results.save(KeyResult.create(ownerId, objective.getId(), form.getTitle(), form.getUnit(), form.getBaseline(), form.getTarget(), direction, clock.instant())).getId();
    }

    @Transactional
    public void record(UUID ownerId, UUID objectiveId, UUID resultId, KeyResultMeasurementForm form) {
        ownObjective(ownerId, objectiveId);
        KeyResult result = results.findByIdAndOwnerId(resultId, ownerId).filter(item -> item.getObjectiveId().equals(objectiveId))
                .orElseThrow(() -> new IllegalArgumentException("That key result is not available."));
        if (form.getValue() == null || form.getDate() == null || form.getDate().isAfter(LocalDate.now(clock.withZone(zones.zone(ownerId)))))
            throw new IllegalArgumentException("Enter a value and a check-in date that is not in the future.");
        measurements.save(KeyResultMeasurement.record(ownerId, result.getId(), form.getValue(), form.getDate(), form.getNotes(), clock.instant()));
    }

    @Transactional public void transition(UUID ownerId, UUID id, String status) { ownObjective(ownerId, id).transition(status, clock.instant()); }

    @Transactional(readOnly = true)
    public ObjectiveDetail detail(UUID ownerId, UUID id) {
        Objective objective = ownObjective(ownerId, id); OkrCycle cycle = ownCycle(ownerId, objective.getCycleId());
        List<KeyResultView> views = results.findAllByOwnerIdAndObjectiveIdOrderByCreatedAtAsc(ownerId, id).stream().map(result -> {
            List<KeyResultMeasurement> history = measurements.findAllByOwnerIdAndKeyResultIdOrderByMeasurementDateDescRecordedAtDesc(ownerId, result.getId());
            BigDecimal current = history.isEmpty() ? null : history.getFirst().getRecordedValue();
            BigDecimal progress = ProgressCalculator.fraction(result.getBaselineValue(), result.getTargetValue(), current);
            return new KeyResultView(result, current, progress, ProgressCalculator.displayPercent(progress), history);
        }).toList();
        List<BigDecimal> measured = views.stream().map(KeyResultView::progress).filter(java.util.Objects::nonNull).toList();
        BigDecimal aggregate = measured.isEmpty() ? null : measured.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(measured.size()), 8, RoundingMode.HALF_UP);
        return new ObjectiveDetail(objective, cycle, views, aggregate, ProgressCalculator.displayPercent(aggregate), measured.size());
    }

    private OkrCycle ownCycle(UUID ownerId, UUID id) { return cycles.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new IllegalArgumentException("That review cycle is not available.")); }
    private Objective ownObjective(UUID ownerId, UUID id) { return objectives.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new IllegalArgumentException("That objective is not available.")); }
}
