package com.taskos.goals.service;

import com.taskos.goals.dto.ReviewForm;
import com.taskos.goals.dto.ReviewWorkspace;
import com.taskos.goals.entity.Review;
import com.taskos.goals.entity.ReviewType;
import com.taskos.goals.repository.GoalMeasurementRepository;
import com.taskos.goals.repository.KeyResultMeasurementRepository;
import com.taskos.goals.repository.ReviewRepository;
import com.taskos.habits.repository.HabitCheckInRepository;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.repository.TaskRepository;
import com.taskos.time.entity.TimeEntryStatus;
import com.taskos.time.repository.TimeEntryRepository;
import com.taskos.time.service.TimeZoneResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    private final ReviewRepository reviews;
    private final TaskRepository tasks;
    private final HabitCheckInRepository habits;
    private final GoalMeasurementRepository goalMeasurements;
    private final KeyResultMeasurementRepository keyResults;
    private final TimeEntryRepository timeEntries;
    private final TimeZoneResolver zones;
    private final Clock clock;

    public ReviewService(ReviewRepository reviews, TaskRepository tasks, HabitCheckInRepository habits,
            GoalMeasurementRepository goalMeasurements, KeyResultMeasurementRepository keyResults,
            TimeEntryRepository timeEntries, TimeZoneResolver zones, Clock clock) {
        this.reviews = reviews; this.tasks = tasks; this.habits = habits; this.goalMeasurements = goalMeasurements;
        this.keyResults = keyResults; this.timeEntries = timeEntries; this.zones = zones; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReviewWorkspace open(UUID ownerId, ReviewType type, LocalDate start) {
        LocalDate end = type == ReviewType.WEEKLY ? start.plusDays(6) : start.withDayOfMonth(start.lengthOfMonth());
        Review review = reviews.findByOwnerIdAndTypeAndPeriodStart(ownerId, type, start).orElseGet(() -> Review.create(ownerId, type, start, end, clock.instant()));
        ZoneId zone = zones.zone(ownerId); Instant from = start.atStartOfDay(zone).toInstant(); Instant until = end.plusDays(1).atStartOfDay(zone).toInstant();
        long completedTasks = tasks.countDashboardCompletedBetween(ownerId, TaskStatus.COMPLETED, from, until);
        long habitCheckIns = habits.countByOwnerIdAndLocalDateBetweenAndPerformedTrue(ownerId, start, end);
        long goalCheckIns = goalMeasurements.countByOwnerIdAndMeasurementDateBetween(ownerId, start, end);
        long keyResultCheckIns = keyResults.countByOwnerIdAndMeasurementDateBetween(ownerId, start, end);
        long focusSeconds = timeEntries.findAllByOwnerIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(ownerId, from, until).stream()
                .filter(entry -> entry.getStatus() == TimeEntryStatus.COMPLETED).mapToLong(entry -> entry.getAccumulatedSeconds()).sum();
        return new ReviewWorkspace(review, form(review), completedTasks, habitCheckIns, goalCheckIns, keyResultCheckIns, focusSeconds, List.of());
    }

    @Transactional
    public void save(UUID ownerId, ReviewType type, LocalDate start, ReviewForm form, boolean complete) {
        if (form.getReflection() != null && form.getReflection().length() > 3000 || form.getWins() != null && form.getWins().length() > 2000 ||
                form.getChallenges() != null && form.getChallenges().length() > 2000 || form.getNextSteps() != null && form.getNextSteps().length() > 2000)
            throw new IllegalArgumentException("Review text is longer than the allowed limit.");
        LocalDate end = type == ReviewType.WEEKLY ? start.plusDays(6) : start.withDayOfMonth(start.lengthOfMonth());
        Review review = reviews.findByOwnerIdAndTypeAndPeriodStart(ownerId, type, start)
                .orElseGet(() -> reviews.save(Review.create(ownerId, type, start, end, clock.instant())));
        review.save(form.getReflection(), form.getWins(), form.getChallenges(), form.getNextSteps(), complete, clock.instant());
    }

    @Transactional(readOnly = true) public List<Review> history(UUID ownerId) { return reviews.findAllByOwnerIdOrderByPeriodStartDesc(ownerId); }

    @Transactional(readOnly = true)
    public ReviewWorkspace detail(UUID ownerId, UUID id) {
        Review review = reviews.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new IllegalArgumentException("That review is not available."));
        ReviewWorkspace workspace = open(ownerId, review.getType(), review.getPeriodStart());
        return new ReviewWorkspace(review, form(review), workspace.completedTasks(), workspace.habitCheckIns(), workspace.goalCheckIns(), workspace.keyResultCheckIns(), workspace.focusSeconds(), List.of());
    }

    public LocalDate currentStart(ReviewType type, UUID ownerId) {
        LocalDate today = LocalDate.now(clock.withZone(zones.zone(ownerId)));
        return type == ReviewType.WEEKLY ? today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) : today.withDayOfMonth(1);
    }

    private ReviewForm form(Review review) {
        ReviewForm form = new ReviewForm(); form.setReflection(review.getReflection()); form.setWins(review.getWins());
        form.setChallenges(review.getChallenges()); form.setNextSteps(review.getNextSteps()); return form;
    }
}
