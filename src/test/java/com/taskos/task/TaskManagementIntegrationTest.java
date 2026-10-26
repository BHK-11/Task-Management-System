package com.taskos.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.auth.service.RegistrationService;
import com.taskos.security.service.DatabaseUserDetailsService;
import com.taskos.task.dto.BulkTaskAction;
import com.taskos.task.dto.BulkTaskActionForm;
import com.taskos.task.dto.ChecklistItemForm;
import com.taskos.task.dto.SubtaskForm;
import com.taskos.task.dto.TagForm;
import com.taskos.task.dto.TaskForm;
import com.taskos.task.entity.ChecklistItem;
import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskActivityType;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.entity.TaskTagColor;
import com.taskos.task.repository.ChecklistItemRepository;
import com.taskos.task.repository.TaskActivityRepository;
import com.taskos.task.repository.TaskRepository;
import com.taskos.task.service.ChecklistService;
import com.taskos.task.service.TaskNotFoundException;
import com.taskos.task.service.TaskRuleException;
import com.taskos.task.service.TaskService;
import com.taskos.task.service.TaskTagService;
import com.taskos.user.entity.UserAccount;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TaskManagementIntegrationTest {
    private static final String PASSWORD = "a sufficiently long test passphrase";

    @Autowired private MockMvc mvc;
    @Autowired private RegistrationService registration;
    @Autowired private DatabaseUserDetailsService userDetails;
    @Autowired private TaskService tasks;
    @Autowired private TaskTagService tags;
    @Autowired private ChecklistService checklist;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskActivityRepository activityRepository;
    @Autowired private ChecklistItemRepository checklistRepository;

    private TestUser alice;
    private TestUser bob;

    @BeforeEach
    void createUsers() {
        alice = createUser("Alice");
        bob = createUser("Bob");
    }

    @Test
    void taskScreensRenderAndCreatePersistsServerSideData() throws Exception {
        mvc.perform(get("/tasks").with(user(alice.principal())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tasks")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Search and filter tasks")));
        mvc.perform(get("/tasks/new").with(user(alice.principal())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Create a task")));
        mvc.perform(get("/tags").with(user(alice.principal())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("All tags")));

        MvcResult created = mvc.perform(post("/tasks").with(user(alice.principal())).with(csrf())
                        .param("title", "Review launch plan")
                        .param("description", "Confirm the final checklist")
                        .param("status", "TODO")
                        .param("priority", "HIGH")
                        .param("startDate", "2026-10-20")
                        .param("dueDate", "2026-10-25"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = created.getResponse().getRedirectedUrl();
        UUID taskId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        Task stored = taskRepository.findById(taskId).orElseThrow();
        assertEquals(alice.account().getId(), stored.getOwner().getId());
        assertEquals(LocalDate.parse("2026-10-25"), stored.getDueDate());
        mvc.perform(get(location).with(user(alice.principal())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Review launch plan")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Activity")));
        mvc.perform(get(location + "/edit").with(user(alice.principal())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Save changes")));
        mvc.perform(post("/tasks").with(user(alice.principal())).param("title", "Missing CSRF"))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherOwnersCannotReadOrMutateTasksAndBulkUpdatesAreAtomic() throws Exception {
        UUID aliceTask = createTask(alice, "Alice private task");
        UUID bobTask = createTask(bob, "Bob private task");

        mvc.perform(get("/tasks/" + aliceTask).with(user(bob.principal())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/tasks/" + aliceTask + "/edit").with(user(bob.principal())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/tasks/" + aliceTask + "/status").with(user(bob.principal())).with(csrf())
                        .param("status", "COMPLETED"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/tasks/" + aliceTask + "/delete").with(user(bob.principal())).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/tasks/bulk").with(user(alice.principal())).with(csrf())
                        .param("action", "COMPLETE").param("taskIds", aliceTask.toString(), bobTask.toString()))
                .andExpect(status().isNotFound());
        assertEquals(TaskStatus.TODO, taskRepository.findById(aliceTask).orElseThrow().getStatus());
        assertEquals(TaskStatus.TODO, taskRepository.findById(bobTask).orElseThrow().getStatus());
        assertThrows(TaskNotFoundException.class, () -> tasks.delete(bob.account().getId(), aliceTask));

        BulkTaskActionForm validBulk = new BulkTaskActionForm();
        validBulk.setTaskIds(List.of(aliceTask));
        validBulk.setAction(BulkTaskAction.COMPLETE);
        assertEquals(1, tasks.bulk(alice.account().getId(), validBulk));
        assertEquals(TaskStatus.COMPLETED, taskRepository.findById(aliceTask).orElseThrow().getStatus());
    }

    @Test
    void subtasksChecklistTagsAndActivityRespectOwnerAndPersist() throws Exception {
        UUID parentId = createTask(alice, "Prepare workshop");
        TagForm tagForm = new TagForm();
        tagForm.setName("Planning");
        tagForm.setColor(TaskTagColor.BLUE);
        UUID aliceTag = tags.create(alice.account().getId(), tagForm);
        UUID bobTag = tags.create(bob.account().getId(), tagForm);

        TaskForm assignForeignTag = form("Wrong owner tag");
        assignForeignTag.setTagIds(List.of(bobTag));
        assertThrows(TaskNotFoundException.class, () -> tasks.create(alice.account().getId(), assignForeignTag));

        SubtaskForm subtaskForm = new SubtaskForm();
        subtaskForm.setTitle("Draft agenda");
        subtaskForm.setPriority(TaskPriority.HIGH);
        UUID childId = tasks.createSubtask(alice.account().getId(), parentId, subtaskForm);
        SubtaskForm secondSubtaskForm = new SubtaskForm();
        secondSubtaskForm.setTitle("Book the room");
        UUID secondChildId = tasks.createSubtask(alice.account().getId(), parentId, secondSubtaskForm);
        assertThrows(TaskRuleException.class, () -> tasks.createSubtask(alice.account().getId(), childId, subtaskForm));

        mvc.perform(get("/tasks").param("selected", parentId.toString()).with(user(alice.principal())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("My Work")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AI Assist")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Expand all")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Draft agenda")));
        mvc.perform(post("/tasks/{parentId}/subtasks/{childId}/toggle", parentId, childId)
                        .with(user(alice.principal())).with(csrf()).param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"completed\":1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"total\":2")));
        assertEquals(TaskStatus.COMPLETED, taskRepository.findById(childId).orElseThrow().getStatus());
        mvc.perform(post("/tasks/{parentId}/subtasks/{childId}/toggle", parentId, childId)
                        .with(user(bob.principal())).with(csrf()).param("status", "TODO"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/ai").param("taskId", parentId.toString()).with(user(alice.principal())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Help me plan the next steps for this task.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Prepare workshop")));
        mvc.perform(get("/ai").param("taskId", parentId.toString()).with(user(bob.principal())))
                .andExpect(status().isNotFound());
        tasks.moveSubtask(alice.account().getId(), parentId, secondChildId, "UP");
        assertEquals(secondChildId, tasks.detail(alice.account().getId(), parentId).subtasks().getFirst().getId());

        ChecklistItemForm checklistForm = new ChecklistItemForm();
        checklistForm.setText("List the speakers");
        checklist.add(alice.account().getId(), parentId, checklistForm);
        ChecklistItem item = checklistRepository.findAllByTask_IdOrderByPositionAscCreatedAtAsc(parentId).getFirst();
        checklistForm.setText("List speakers and timing");
        checklist.update(alice.account().getId(), parentId, item.getId(), checklistForm);
        checklist.toggle(alice.account().getId(), parentId, item.getId());
        assertTrue(checklistRepository.findById(item.getId()).orElseThrow().isCompleted());

        TaskForm withTag = form("Tagged task");
        withTag.setTagIds(List.of(aliceTag));
        UUID taggedTask = tasks.create(alice.account().getId(), withTag);
        assertEquals(1, taskRepository.findDetailByIdAndOwner(taggedTask, alice.account().getId()).orElseThrow().getTags().size());

        tasks.transition(alice.account().getId(), parentId, TaskStatus.COMPLETED);
        assertTrue(activityRepository.findAllByOwner_IdAndTaskIdOrderByOccurredAtDesc(
                alice.account().getId(), parentId, org.springframework.data.domain.PageRequest.of(0, 30))
                .stream().anyMatch(event -> event.getEventType() == TaskActivityType.TASK_COMPLETED));
        mvc.perform(get("/tasks/" + parentId).with(user(alice.principal())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Draft agenda")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("List speakers and timing")));
    }

    @Test
    void searchArchiveRestoreAndDeleteHistoryWork() throws Exception {
        UUID match = createTask(alice, "Quarterly review packet");
        createTask(alice, "Garden supplies");
        mvc.perform(get("/tasks").with(user(alice.principal())).param("q", "quarterly"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Quarterly review packet")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Garden supplies"))));
        mvc.perform(get("/tasks").with(user(alice.principal())).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Page 1 of 2")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Next")));
        tasks.archive(alice.account().getId(), match);
        assertTrue(taskRepository.findById(match).orElseThrow().getArchivedAt() != null);
        mvc.perform(get("/tasks").with(user(alice.principal())).param("view", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Quarterly review packet")));
        tasks.restore(alice.account().getId(), match);
        assertNull(taskRepository.findById(match).orElseThrow().getArchivedAt());
        tasks.delete(alice.account().getId(), match);
        assertFalse(taskRepository.existsById(match));
        assertTrue(activityRepository.findAll().stream().anyMatch(event -> match.equals(event.getTaskId())
                && event.getEventType() == TaskActivityType.TASK_DELETED));
    }

    @Test
    void executionWorkspaceMovesBetweenPagesWithTheActiveFilters() throws Exception {
        List<UUID> ids = IntStream.range(0, 21)
                .mapToObj(index -> createTask(alice, "Workspace task %02d".formatted(index)))
                .toList();

        MvcResult firstPage = mvc.perform(get("/tasks").with(user(alice.principal()))
                        .param("size", "20").param("sort", "TITLE").param("q", "Workspace")
                        .param("status", "TODO").param("selected", ids.get(19).toString()))
                .andExpect(status().isOk()).andReturn();
        String nextUrl = (String) firstPage.getModelAndView().getModel().get("nextTaskUrl");
        assertTrue(nextUrl.contains("page=1"));
        assertTrue(nextUrl.contains("selected=" + ids.get(20)));
        assertTrue(nextUrl.contains("q=Workspace") && nextUrl.contains("status=TODO") && nextUrl.contains("sort=TITLE"));

        MvcResult lastPage = mvc.perform(get("/tasks").with(user(alice.principal()))
                        .param("page", "1").param("size", "20").param("sort", "TITLE")
                        .param("q", "Workspace").param("status", "TODO")
                        .param("selected", ids.get(20).toString()))
                .andExpect(status().isOk()).andReturn();
        String previousUrl = (String) lastPage.getModelAndView().getModel().get("previousTaskUrl");
        assertTrue(previousUrl.contains("page=0"));
        assertTrue(previousUrl.contains("selected=" + ids.get(19)));
        assertTrue(previousUrl.contains("q=Workspace") && previousUrl.contains("status=TODO") && previousUrl.contains("sort=TITLE"));
    }

    @Test
    void executionWorkspaceEditsPriorityAndDeadlineInline() throws Exception {
        UUID taskId = createTask(alice, "Update launch outline");
        mvc.perform(get("/tasks").with(user(alice.principal())).param("selected", taskId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Edit priority and deadline")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("workspace-deadline-mobile")));

        mvc.perform(post("/tasks/{id}", taskId).with(user(alice.principal())).with(csrf())
                        .param("title", "Update launch outline").param("description", "")
                        .param("status", "TODO").param("priority", "URGENT")
                        .param("startDate", "").param("dueDate", "2026-10-31")
                        .param("version", "0").param("workspaceUrl", "/tasks?selected=" + taskId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tasks?selected=" + taskId));

        var updated = taskRepository.findById(taskId).orElseThrow();
        assertEquals(TaskPriority.URGENT, updated.getPriority());
        assertEquals(java.time.LocalDate.parse("2026-10-31"), updated.getDueDate());
    }

    private UUID createTask(TestUser owner, String title) {
        return tasks.create(owner.account().getId(), form(title));
    }

    private TaskForm form(String title) {
        TaskForm form = new TaskForm();
        form.setTitle(title);
        form.setStatus(TaskStatus.TODO);
        form.setPriority(TaskPriority.MEDIUM);
        return form;
    }

    private TestUser createUser(String label) {
        String email = label.toLowerCase() + "+" + UUID.randomUUID() + "@example.test";
        UserAccount account = registration.register(label + " Task User", email, PASSWORD);
        return new TestUser(account, (TaskOsUserDetails) userDetails.loadUserByUsername(email));
    }

    private record TestUser(UserAccount account, TaskOsUserDetails principal) { }
}
