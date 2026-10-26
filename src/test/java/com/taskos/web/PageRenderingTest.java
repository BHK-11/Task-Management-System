package com.taskos.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.taskos.ai.dto.AiActionType;
import com.taskos.ai.dto.AiPlannerPlanDraft;
import com.taskos.ai.dto.AiPlannerResponse;
import com.taskos.ai.dto.AiPlannerStepDraft;
import com.taskos.ai.service.AiPlanPersistenceService;
import com.taskos.ai.service.AiModelPreferenceService;
import com.taskos.knowledge.entity.AiMemoryCategory;
import com.taskos.knowledge.entity.AiMemory;
import com.taskos.knowledge.service.AiMemoryService;
import com.taskos.knowledge.repository.AiMemoryRepository;
import com.taskos.browser.entity.BrowserJobStatus;
import com.taskos.browser.service.BrowserObservationJobService;
import com.taskos.auth.service.RegistrationService;
import com.taskos.security.service.DatabaseUserDetailsService;
import com.taskos.task.dto.TaskForm;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.service.TaskService;
import com.taskos.time.dto.RecurrenceForm;
import com.taskos.time.entity.RecurrenceFrequency;
import com.taskos.time.service.RecurrenceService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PageRenderingTest {
    @Autowired
    private MockMvc mvc;
    @Autowired private RegistrationService registration;
    @Autowired private DatabaseUserDetailsService userDetails;
    @Autowired private TaskService tasks;
    @Autowired private RecurrenceService recurrences;
    @Autowired private AiPlanPersistenceService aiPlans;
    @Autowired private AiModelPreferenceService aiModelPreferences;
    @Autowired private AiMemoryService aiMemories;
    @Autowired private AiMemoryRepository aiMemoryRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private BrowserObservationJobService browserJobs;

    @Test
    void publicPagesAndStaticAssetsRenderThroughThymeleaf() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/home"))
                .andExpect(content().string(containsString("Make room for")))
                .andExpect(content().string(containsString("Plan clearly. Work together. Keep moving.")));
        mvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Java-powered work platform")));
        mvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("prefers-reduced-motion")))
                .andExpect(content().string(containsString("safe-area-inset-bottom")));
        mvc.perform(get("/js/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event.ctrlKey")))
                .andExpect(content().string(containsString("data-command-project-search")));
    }

    @Test
    void guestPreviewIsPublicButSignedInWorkspaceRequiresAuthentication() throws Exception {
        mvc.perform(get("/preview"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/workspace-preview"))
                .andExpect(content().string(containsString("does not create an account")))
                .andExpect(content().string(containsString("simple and advanced modes")))
                .andExpect(content().string(containsString("Sign in to use private task, project, team")));
        mvc.perform(get("/roadmap"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UI foundation and application integration")))
                .andExpect(content().string(containsString("Advanced reports and saved dashboards")));
        mvc.perform(get("/app"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/login"));
    }

    @Test
    void phaseTwelveCopilotShowsSetupStateForAnAuthenticatedOwner() throws Exception {
        var account = registration.register("Copilot Viewer", "copilot-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var principal = userDetails.loadUserByUsername(account.getEmail());
        mvc.perform(get("/ai"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/ai").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("ai/copilot"))
                .andExpect(content().string(containsString("AI Copilot")))
                .andExpect(content().string(containsString("Setup required")))
                .andExpect(content().string(containsString("provider key and model only to the private AI service environment")))
                .andExpect(content().string(containsString("Gmail and WhatsApp sending and external agents are not connected")));
    }

    @Test
    void communicationsCenterShowsUnconfiguredProvidersWithoutInventingInboxData() throws Exception {
        var account = registration.register("Communications Viewer", "communications-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var principal = userDetails.loadUserByUsername(account.getEmail());

        mvc.perform(get("/communications"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/communications").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("communications/index"))
                .andExpect(content().string(containsString("No communication accounts are connected")))
                .andExpect(content().string(containsString("NOT_CONNECTED")))
                .andExpect(content().string(containsString("Gmail")))
                .andExpect(content().string(containsString("Microsoft Outlook")))
                .andExpect(content().string(containsString("WhatsApp Business")))
                .andExpect(content().string(containsString("Transcript import is not available")))
                .andExpect(content().string(containsString("Draft files are not sent or delivered")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Approve and send"))));
        mvc.perform(get("/css/communications.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("@media (max-width: 680px)")));
    }

    @Test
    void aiModelSettingsAreAuthenticatedAndPersistPrivacyPreferencesPerUser() throws Exception {
        var account = registration.register("AI Settings Owner", "ai-settings-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var principal = userDetails.loadUserByUsername(account.getEmail());
        mvc.perform(get("/settings/ai"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/settings/ai").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("account/ai-model-settings"))
                .andExpect(content().string(containsString("Model selection")))
                .andExpect(content().string(containsString("Allow cloud processing")))
                .andExpect(content().string(containsString("Input tokens")))
                .andExpect(content().string(containsString("Cost is unknown")));
        mvc.perform(post("/settings/ai").with(user(principal)).with(csrf())
                        .param("mode", "PRIVATE").param("allowCloudProcessing", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings/ai"));
        var saved = aiModelPreferences.get(account.getId());
        org.junit.jupiter.api.Assertions.assertEquals(com.taskos.ai.dto.AiModelMode.PRIVATE, saved.mode());
        org.junit.jupiter.api.Assertions.assertFalse(saved.allowCloudProcessing());
        org.junit.jupiter.api.Assertions.assertFalse(saved.allowFallback());
    }

    @Test
    void persistentMemoryRequiresConsentAndIsOwnerScopedCorrectableAndDeletable() throws Exception {
        var owner = registration.register("Memory Owner", "memory-owner-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var other = registration.register("Other Memory Owner", "memory-other-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var ownerPrincipal = userDetails.loadUserByUsername(owner.getEmail());
        var otherPrincipal = userDetails.loadUserByUsername(other.getEmail());

        mvc.perform(get("/settings/memory"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/settings/memory").with(user(ownerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(view().name("knowledge/memory-settings"))
                .andExpect(content().string(containsString("Persistent memory")))
                .andExpect(content().string(containsString("AI suggestions are not stored automatically")));
        org.junit.jupiter.api.Assertions.assertFalse(aiMemories.preference(owner.getId()).enabled());

        mvc.perform(post("/settings/memory").with(user(ownerPrincipal)).with(csrf())
                        .param("category", "PREFERENCE").param("content", "I prefer concise weekly status updates."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Turn memory on first.")));
        mvc.perform(post("/settings/memory/consent").with(user(ownerPrincipal)).with(csrf()).param("enabled", "true"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/settings/memory"));
        mvc.perform(post("/settings/memory").with(user(ownerPrincipal)).with(csrf())
                        .param("category", "PREFERENCE").param("content", "I prefer concise weekly status updates."))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/settings/memory"));
        mvc.perform(post("/settings/memory").with(user(ownerPrincipal)).with(csrf())
                        .param("category", "PREFERENCE").param("content", "password: example secret"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("looks like a password or secret")));

        var original = aiMemories.list(owner.getId()).getFirst();
        mvc.perform(get("/settings/memory").with(user(ownerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("concise weekly status updates")))
                .andExpect(content().string(containsString("Confirmed by you")));
        mvc.perform(get("/settings/memory").with(user(otherPrincipal)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("concise weekly status updates"))));
        mvc.perform(post("/settings/memory/" + original.getId() + "/delete").with(user(otherPrincipal)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertEquals(1, aiMemories.list(owner.getId()).size());

        mvc.perform(post("/settings/memory/" + original.getId() + "/correct").with(user(ownerPrincipal)).with(csrf())
                        .param("category", "WORK_STYLE").param("content", "I prefer concise project updates with clear next steps."))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertEquals(1, aiMemories.list(owner.getId()).size());
        org.junit.jupiter.api.Assertions.assertEquals(AiMemoryCategory.WORK_STYLE, aiMemories.list(owner.getId()).getFirst().getCategory());
        org.junit.jupiter.api.Assertions.assertEquals(1, aiMemories.retrieve(owner.getId(), "project updates", 5).size());
        org.junit.jupiter.api.Assertions.assertTrue(aiMemories.retrieve(other.getId(), "project updates", 5).isEmpty());

        mvc.perform(post("/settings/memory/" + aiMemories.list(owner.getId()).getFirst().getId() + "/delete")
                        .with(user(ownerPrincipal)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertTrue(aiMemories.list(owner.getId()).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(aiMemoryRepository.findAllByOwnerIdOrderByCreatedAtDesc(owner.getId()).isEmpty());
        mvc.perform(post("/settings/memory/consent").with(user(ownerPrincipal)).with(csrf()).param("enabled", "false"))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertFalse(aiMemories.preference(owner.getId()).enabled());
    }

    @Test
    @Transactional
    void concurrentMemoryCorrectionRejectsStaleWrites() {
        var owner = registration.register("Concurrent Memory Owner", "memory-version-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        aiMemories.setEnabled(owner.getId(), true);
        AiMemory saved = aiMemories.add(owner.getId(), AiMemoryCategory.PREFERENCE, "Use concise updates.");
        entityManager.flush();
        entityManager.clear();

        AiMemory stale = aiMemoryRepository.findById(saved.getId()).orElseThrow();
        entityManager.detach(stale);
        AiMemory current = aiMemoryRepository.findById(saved.getId()).orElseThrow();
        current.supersede(Instant.now());
        aiMemoryRepository.flush();

        stale.supersede(Instant.now());
        org.junit.jupiter.api.Assertions.assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> aiMemoryRepository.save(stale));
    }

    @Test
    void knowledgeLibraryIsAuthenticatedAndExplainsSupportedFilesAndPrivateStorage() throws Exception {
        var account = registration.register("Knowledge Viewer", "knowledge-viewer-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var principal = userDetails.loadUserByUsername(account.getEmail());
        mvc.perform(get("/knowledge"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/knowledge").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("knowledge/library"))
                .andExpect(content().string(containsString("PDF, DOCX, XLSX, PPTX, TXT, or Markdown")))
                .andExpect(content().string(containsString("Original files are stored outside the public web directory")))
                .andExpect(content().string(containsString("Search indexed text")));
    }

    @Test
    void browserStudioIsAuthenticatedAndObservationHistoryIsOwnerScoped() throws Exception {
        var owner = registration.register("Browser Owner", "browser-owner-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var other = registration.register("Other Browser Owner", "browser-other-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var ownerPrincipal = userDetails.loadUserByUsername(owner.getEmail());
        var otherPrincipal = userDetails.loadUserByUsername(other.getEmail());
        browserJobs.record(owner.getId(), "https://docs.example.test", BrowserJobStatus.COMPLETED,
                "Public course guide", null);

        mvc.perform(get("/browser")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/browser").with(user(ownerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(view().name("browser/studio"))
                .andExpect(content().string(containsString("Browser Studio")))
                .andExpect(content().string(containsString("Read-only mode")))
                .andExpect(content().string(containsString("Public course guide")))
                .andExpect(content().string(containsString("Worker not configured")));
        mvc.perform(get("/browser").with(user(otherPrincipal)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Public course guide"))));
    }

    @Test
    @Transactional
    void agentManagementShowsOwnerScopedPlanHistoryAndNoFakeProviderConnection() throws Exception {
        var owner = registration.register("Agent Page Owner", "agent-owner-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var other = registration.register("Other Agent Owner", "agent-other-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var principal = userDetails.loadUserByUsername(owner.getEmail());
        aiPlans.save(owner.getId(), "Owner-visible plan", new AiPlannerResponse(
                new AiPlannerPlanDraft("Owner-visible plan", "en", List.of(), List.of()), "test-model", 2, 3), Instant.now());
        aiPlans.save(other.getId(), "Private other plan", new AiPlannerResponse(
                new AiPlannerPlanDraft("Private other plan", "en", List.of(), List.of()), "test-model", 2, 3), Instant.now());

        mvc.perform(get("/agents"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/agents").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("agents/index"))
                .andExpect(content().string(containsString("TASK OS planner")))
                .andExpect(content().string(containsString("Setup required")))
                .andExpect(content().string(containsString("Provider health")))
                .andExpect(content().string(containsString("Owner-visible plan")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Private other plan"))))
                .andExpect(content().string(containsString("Background jobs are not tracked yet")))
                .andExpect(content().string(containsString("No TASK OS memory store")));
    }

    @Test
    @Transactional
    void savedPlanReviewRendersEditableAndUnsupportedSteps() throws Exception {
        var account = registration.register("Plan Viewer", "plan-view-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var principal = userDetails.loadUserByUsername(account.getEmail());
        var plan = aiPlans.save(account.getId(), "Prepare for Java exams", new AiPlannerResponse(
                new AiPlannerPlanDraft("Prepare for Java exams", "en", List.of(), List.of(
                        new AiPlannerStepDraft(AiActionType.CREATE_TASK, "Review inheritance", "Read notes", TaskPriority.HIGH,
                                null, List.of()),
                        new AiPlannerStepDraft(AiActionType.UNSUPPORTED, "Schedule study block", "Calendar actions are unavailable",
                                null, null, List.of()))), "test-model", 10, 20), Instant.now());

        mvc.perform(get("/ai").param("planId", plan.getId().toString()).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Prepare for Java exams")))
                .andExpect(content().string(containsString("Review inheritance")))
                .andExpect(content().string(containsString("Save review changes")))
                .andExpect(content().string(containsString("Not available in this copilot yet")))
                .andExpect(content().string(containsString("Calendar actions are unavailable")));
    }

    @Test
    void signedInAppEntryLeadsToPrivateDashboardAndUnknownRouteUsesSafeNotFoundPage() throws Exception {
        var account = registration.register("Dashboard Viewer", "dashboard-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var principal = userDetails.loadUserByUsername(account.getEmail());
        mvc.perform(get("/app").with(user(principal)))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/dashboard"));
        mvc.perform(get("/dashboard").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PERSONAL COMMAND CENTER")))
                .andExpect(content().string(containsString("Your workspace is ready.")))
                .andExpect(content().string(containsString("Primary navigation")))
                .andExpect(content().string(containsString("More navigation")))
                .andExpect(content().string(containsString("global-command-dialog")))
                .andExpect(content().string(containsString("data-command-task-search")));
        mvc.perform(get("/admin/users").with(user(principal)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/not-a-real-page").with(user(principal)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("This page does not exist or is not available yet.")))
                .andExpect(content().string(containsString("Back to home")));
    }

    @Test
    void phaseEightTimePagesRenderForAnAuthenticatedOwner() throws Exception {
        var account = registration.register("Time Viewer", "time-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var principal = userDetails.loadUserByUsername(account.getEmail());
        mvc.perform(get("/time").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/calendar").param("view", "month").param("date", "2026-10-25").with(user(principal)))
                .andExpect(status().isOk());
        mvc.perform(get("/calendar").param("view", "week").param("date", "2026-10-25").with(user(principal)))
                .andExpect(status().isOk());
        mvc.perform(get("/calendar").param("view", "day").param("date", "2026-10-25").with(user(principal)))
                .andExpect(status().isOk());
        mvc.perform(get("/time/blocks").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/focus/pomodoro").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/focus/deep-work").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/focus/history").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/time/tracking").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/tasks/recurring").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/reminders").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/time/insights").with(user(principal))).andExpect(status().isOk());
    }

    @Test
    void phaseNineGrowthPagesRenderForAnAuthenticatedOwner() throws Exception {
        var account = registration.register("Growth Viewer", "growth-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var principal = userDetails.loadUserByUsername(account.getEmail());
        mvc.perform(get("/goals").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/goals/new").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/okrs").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/okrs/new").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/habits").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/habits/new").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/reviews/weekly").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/reviews/monthly").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/reviews/history").with(user(principal))).andExpect(status().isOk());
    }

    @Test
    void goalFormShowsValidationAndDeterministicPlanningPromptsAndRequiresCsrf() throws Exception {
        var account = registration.register("Goal Form Viewer", "goal-form-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var principal = userDetails.loadUserByUsername(account.getEmail());
        mvc.perform(post("/goals").with(user(principal)).param("title", ""))
                .andExpect(status().isForbidden());
        mvc.perform(post("/goals").with(user(principal)).with(csrf()).param("title", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Review these fields:")))
                .andExpect(content().string(containsString("Describe the specific result you want to achieve.")))
                .andExpect(content().string(containsString("Add a measurable success criterion.")))
                .andExpect(content().string(containsString("Choose a target date.")));
    }

    @Test
    void automationCenterAndBuilderRenderForAnAuthenticatedOwner() throws Exception {
        var account = registration.register("Automation Viewer", "automation-view-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var principal = userDetails.loadUserByUsername(account.getEmail());
        mvc.perform(get("/automations").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Automation Center")))
                .andExpect(content().string(containsString("Create automation")));
        mvc.perform(get("/automations/new").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Build an automation")))
                .andExpect(content().string(containsString("Configure actions")));
        mvc.perform(post("/automations").with(user(principal)).with(csrf())
                        .param("name", "Create follow-up")
                        .param("description", "Add a task after new work arrives")
                        .param("scopeType", "PERSONAL")
                        .param("triggerType", "TASK_CREATED")
                        .param("conditionLogic", "AND")
                        .param("actionTypes[0]", "CREATE_TASK")
                        .param("actionTitles[0]", "Review new task")
                        .param("actionPriorities[0]", "MEDIUM")
                        .param("maxAttempts", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/automations?scopeType=PERSONAL"));
        mvc.perform(get("/automations").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Create follow-up")));
    }

    @Test
    void taskDetailEntryPointsPreselectOnlyTheOwnersTask() throws Exception {
        var account = registration.register("Task Time Viewer", "task-time-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var principal = userDetails.loadUserByUsername(account.getEmail());
        TaskForm form = new TaskForm(); form.setTitle("Prepare release");
        UUID taskId = tasks.create(account.getId(), form);
        RecurrenceForm recurrence = new RecurrenceForm(); recurrence.setSourceTaskId(taskId);
        recurrence.setFrequency(RecurrenceFrequency.DAILY); recurrence.setStartDate(LocalDate.now());
        recurrence.setLocalTime(LocalTime.NOON); recurrences.create(account.getId(), recurrence);
        mvc.perform(get("/calendar").param("view", "day").param("taskId", taskId.toString()).with(user(principal)))
                .andExpect(status().isOk());
        mvc.perform(get("/focus/pomodoro").param("taskId", taskId.toString()).with(user(principal)))
                .andExpect(status().isOk());
        mvc.perform(get("/time/tracking").param("taskId", taskId.toString()).with(user(principal)))
                .andExpect(status().isOk());
        mvc.perform(get("/tasks/recurring").with(user(principal))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit future template")));
    }
}
