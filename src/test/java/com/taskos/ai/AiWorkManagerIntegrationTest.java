package com.taskos.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskos.ai.dto.AiActionType;
import com.taskos.ai.dto.AiPlanStatus;
import com.taskos.ai.dto.AiWorkPlannerPlanDraft;
import com.taskos.ai.dto.AiWorkPlannerResponse;
import com.taskos.ai.dto.AiWorkPlannerStepDraft;
import com.taskos.ai.service.AiArtifactNotFoundException;
import com.taskos.ai.service.AiArtifactStorageService;
import com.taskos.ai.service.AiPlanExecutionService;
import com.taskos.ai.service.AiPlanPersistenceService;
import com.taskos.ai.service.AiPlanService;
import com.taskos.ai.service.AiPlannerClient;
import com.taskos.auth.service.RegistrationService;
import com.taskos.security.service.DatabaseUserDetailsService;
import com.taskos.task.entity.TaskPriority;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiWorkManagerIntegrationTest {
    @TempDir static Path tempDir;
    private static final String isolatedDatabase = "jdbc:h2:mem:taskos_work_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    @DynamicPropertySource
    static void artifactStorage(DynamicPropertyRegistry registry) {
        registry.add("taskos.ai.artifact-directory", () -> tempDir.resolve("private-artifacts").toString());
        registry.add("spring.datasource.url", () -> isolatedDatabase);
    }

    @Autowired private RegistrationService registration;
    @Autowired private DatabaseUserDetailsService userDetails;
    @Autowired private AiPlanPersistenceService persistence;
    @Autowired private AiPlanService plans;
    @Autowired private AiPlanExecutionService execution;
    @Autowired private AiArtifactStorageService artifacts;
    @Autowired private MockMvc mvc;
    @MockitoBean private AiPlannerClient planner;

    @Test
    void approvedMixedPlanCreatesTaskDraftAndPrivateVerifiedDownload() throws Exception {
        var owner = registration.register("Work manager owner", "work-owner-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var other = registration.register("Other work manager", "work-other-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        String document = "## Plan\nReview the examples.";
        String email = "Subject: Review\n\nPlease see the attached plan.";
        byte[] documentBytes = officeDocument();
        when(planner.generateArtifact(eq("docx"), eq("Study guide"), eq(document))).thenReturn(documentBytes);
        when(planner.generateArtifact(eq("txt"), eq("Email draft"), eq(email))).thenReturn(email.getBytes(StandardCharsets.UTF_8));
        var plan = persistence.saveWork(owner.getId(), "Prepare study materials", new AiWorkPlannerResponse(
                new AiWorkPlannerPlanDraft("Prepare study materials", "en", List.of(), List.of(
                        new AiWorkPlannerStepDraft(AiActionType.CREATE_TASK, "Review Java", "Study inheritance", TaskPriority.HIGH, null, List.of(), ""),
                        new AiWorkPlannerStepDraft(AiActionType.GENERATE_DOCX, "Study guide", "A short guide", null, null, List.of(0), document),
                        new AiWorkPlannerStepDraft(AiActionType.DRAFT_EMAIL, "Email draft", "Draft only", null, null, List.of(), email))),
                "test-model", 7, 11), Instant.now());

        assertEquals(AiPlanStatus.QUEUED, plans.approve(owner.getId(), plan.getId()).getStatus());
        execution.executeNext();
        execution.executeNext();
        execution.executeNext();
        var completed = plans.detail(owner.getId(), plan.getId());
        assertEquals(AiPlanStatus.COMPLETED, completed.getStatus(), completed.getSteps().stream()
                .map(step -> step.getStepIndex() + ":" + step.getStatus() + ":" + step.getResultSummary()).toList().toString());
        assertNotNull(completed.getSteps().get(0).getResultTaskId());
        UUID artifactId = completed.getSteps().get(1).getResultArtifactId();
        assertNotNull(artifactId);
        assertNotNull(completed.getSteps().get(2).getResultArtifactId());

        var principal = userDetails.loadUserByUsername(owner.getEmail());
        mvc.perform(get("/ai/artifacts/{id}", artifactId).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Study%20guide.docx")))
                .andExpect(content().bytes(documentBytes));
        mvc.perform(get("/ai/artifacts/{id}", artifactId)
                        .with(user(userDetails.loadUserByUsername(other.getEmail()))))
                .andExpect(status().isNotFound());
        assertThrows(AiArtifactNotFoundException.class, () -> artifacts.load(other.getId(), artifactId));
    }

    private byte[] officeDocument() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String entry : List.of("[Content_Types].xml", "_rels/.rels", "word/document.xml")) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write("<document>verified</document>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
