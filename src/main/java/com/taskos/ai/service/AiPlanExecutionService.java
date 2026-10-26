package com.taskos.ai.service;

import com.taskos.ai.dto.AiActionType;
import com.taskos.ai.dto.AiPlanStepStatus;
import com.taskos.ai.dto.AiPlannerStepDraft;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
public class AiPlanExecutionService {
    private static final Logger log = LoggerFactory.getLogger(AiPlanExecutionService.class);
    private final AiPlanExecutionPersistenceService persistence;
    private final AiPlannerClient planner;

    public AiPlanExecutionService(AiPlanExecutionPersistenceService persistence, AiPlannerClient planner) {
        this.persistence = persistence; this.planner = planner;
    }

    public void executeNext() {
        Optional<UUID> claimed = persistence.claimNext();
        if (claimed.isEmpty()) return;
        UUID planId = claimed.get();
        AiPlanExecutionPersistenceService.PlanWork plan = persistence.load(planId);
        Map<Integer, AiPlanExecutionPersistenceService.StepWork> byIndex = new HashMap<>();
        plan.steps().forEach(step -> byIndex.put(step.index(), step));
        Map<Integer, AiPlanStepStatus> status = new HashMap<>();
        plan.steps().forEach(step -> status.put(step.index(), step.status()));
        List<AiPlannerStepDraft> drafts = plan.steps().stream().map(step -> new AiPlannerStepDraft(step.actionType(),
                step.title(), step.description(), step.priority(), step.dueDate(), step.dependencies())).toList();

        for (int index : AiPlanService.topologicalOrderDrafts(drafts)) {
            var step = byIndex.get(index);
            AiPlanStepStatus current = status.get(index);
            if (current != AiPlanStepStatus.PENDING && current != AiPlanStepStatus.RUNNING) continue;
            boolean failedDependency = step.dependencies().stream().anyMatch(dependency -> switch (status.get(dependency)) {
                case FAILED, BLOCKED, UNSUPPORTED -> true;
                case PENDING, RUNNING, COMPLETED -> false;
            });
            if (failedDependency) {
                persistence.blockStep(planId, index);
                status.put(index, AiPlanStepStatus.BLOCKED);
                continue;
            }
            if (!step.dependencies().stream().allMatch(dependency -> status.get(dependency) == AiPlanStepStatus.COMPLETED)) continue;
            runStep(planId, step);
            persistence.finishIfDone(planId);
            return;
        }
        persistence.finishIfDone(planId);
    }

    private void runStep(UUID planId, AiPlanExecutionPersistenceService.StepWork step) {
        persistence.startStep(planId, step.index());
        try {
            if (step.actionType() == AiActionType.CREATE_TASK) {
                persistence.createTask(planId, step.index());
                return;
            }
            String kind = switch (step.actionType()) {
                case GENERATE_DOCX -> "docx";
                case GENERATE_PDF -> "pdf";
                case GENERATE_XLSX -> "xlsx";
                case GENERATE_PPTX -> "pptx";
                case DRAFT_EMAIL, DRAFT_MESSAGE -> "txt";
                case CREATE_TASK, UNSUPPORTED -> throw new IllegalArgumentException("Unsupported AI action");
            };
            byte[] bytes = planner.generateArtifact(kind, step.title(), step.content());
            String contentType = switch (kind) {
                case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                case "pdf" -> MediaType.APPLICATION_PDF_VALUE;
                default -> "text/plain; charset=utf-8";
            };
            verify(kind, step.content(), bytes);
            String filename = safeFilename(step.title(), kind);
            persistence.saveArtifact(planId, step.index(), filename, contentType, bytes);
        } catch (RuntimeException ex) {
            log.warn("AI work step failed: plan={}, step={}, action={}, errorType={}", planId, step.index(),
                    step.actionType(), ex.getClass().getSimpleName());
            persistence.failStep(planId, step.index());
        }
    }

    private void verify(String kind, String expectedContent, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 10 * 1024 * 1024)
            throw new AiCopilotException("The generated file failed its size check.");
        if (kind.equals("txt")) {
            String decoded = ByteBufferSupport.decode(bytes);
            if (!decoded.equals(expectedContent)) throw new AiCopilotException("The text draft failed verification.");
            return;
        }
        if (kind.equals("pdf")) {
            String start = new String(bytes, 0, Math.min(bytes.length, 8), StandardCharsets.US_ASCII);
            String tail = new String(bytes, Math.max(0, bytes.length - 64), Math.min(bytes.length, 64), StandardCharsets.US_ASCII).trim();
            if (!start.startsWith("%PDF-") || !tail.endsWith("%%EOF")) throw new AiCopilotException("The PDF failed verification.");
            return;
        }
        Set<String> requiredEntries = switch (kind) {
            case "docx" -> Set.of("[Content_Types].xml", "_rels/.rels", "word/document.xml");
            case "xlsx" -> Set.of("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels");
            case "pptx" -> Set.of("[Content_Types].xml", "_rels/.rels", "ppt/presentation.xml", "ppt/_rels/presentation.xml.rels");
            default -> throw new AiCopilotException("The requested format is not supported.");
        };
        Set<String> found = new HashSet<>();
        long uncompressed = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (requiredEntries.contains(entry.getName())) found.add(entry.getName());
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    uncompressed += read;
                    if (uncompressed > 25L * 1024 * 1024) throw new AiCopilotException("The document exceeded its verification limit.");
                }
            }
        } catch (IOException ex) {
            throw new AiCopilotException("The generated office document failed verification.", ex);
        }
        if (!found.containsAll(requiredEntries)) throw new AiCopilotException("The generated office document failed verification.");
    }

    private String safeFilename(String title, String extension) {
        String base = title.replaceAll("[\\p{Cntrl}/\\\\:*?\"<>|]", "-").replaceAll("\\s+", " ").strip();
        base = base.replaceAll("^[. ]+|[. ]+$", "");
        if (base.isEmpty()) base = "deliverable";
        if (base.length() > 150) base = base.substring(0, 150).strip();
        return base + "." + extension;
    }

    private static final class ByteBufferSupport {
        private static String decode(byte[] bytes) {
            try {
                return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            } catch (java.nio.charset.CharacterCodingException ex) {
                throw new AiCopilotException("The text draft is not valid UTF-8.", ex);
            }
        }
    }
}
