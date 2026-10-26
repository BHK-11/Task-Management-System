package com.taskos.common;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class FeatureAvailabilityService {
    private final List<FeatureCard> features = List.of(
            new FeatureCard("Responsive workspace shell", "Desktop navigation, a tablet drawer, and phone navigation with safe-area support.", FeatureStatus.AVAILABLE, "Foundation"),
            new FeatureCard("Theme preferences", "Light, dark and system themes available as browser and account preferences.", FeatureStatus.AVAILABLE, "Foundation"),
            new FeatureCard("Simple and advanced modes", "Choose a quieter navigation view or reveal advanced tools without changing permissions.", FeatureStatus.AVAILABLE, "Foundation"),
            new FeatureCard("Global command search", "Navigate to pages or search personal tasks and projects through their existing access-controlled filters.", FeatureStatus.AVAILABLE, "Foundation"),
            new FeatureCard("PostgreSQL schema foundation", "Environment based connection settings with versioned Flyway migrations.", FeatureStatus.AVAILABLE, "Foundation"),
            new FeatureCard("Personal task management", "Create, organize, and track personal tasks with subtasks, checklists, and tags.", FeatureStatus.AVAILABLE, "Work management"),
            new FeatureCard("Projects and Kanban", "Organize canonical tasks into private projects with custom boards, milestones, and reusable templates.", FeatureStatus.AVAILABLE, "Work management"),
            new FeatureCard("Sprints and advanced planning", "Plan sprint work and use project scheduling, dependencies, baselines and critical path views.", FeatureStatus.AVAILABLE, "Planning"),
            new FeatureCard("Goals, OKRs and habits", "Connect measurable outcomes, personal objectives, scheduled habits and reviews to everyday work.", FeatureStatus.AVAILABLE, "Goals"),
            new FeatureCard("Scheduling and focus", "Plan calendar blocks, focus sessions, time tracking, recurring tasks and in-app reminders.", FeatureStatus.AVAILABLE, "Time"),
            new FeatureCard("Teams and automation", "Coordinate shared workspaces, assignments, approvals, and rule-based workflows.", FeatureStatus.AVAILABLE, "Collaboration"),
            new FeatureCard("AI Copilot and analytics", "Review approved AI task outcomes and personal task, time, AI, and automation metrics.", FeatureStatus.AVAILABLE, "Intelligence"),
            new FeatureCard("Advanced reports and saved dashboards", "Build configurable reports, save dashboard layouts, and generate scheduled PDF or workbook exports.", FeatureStatus.PLANNED, "Analytics"));

    public List<FeatureCard> allFeatures() {
        return features;
    }

    public List<FeatureCard> plannedFeatures() {
        return features.stream().filter(feature -> feature.status() == FeatureStatus.PLANNED).toList();
    }
}
