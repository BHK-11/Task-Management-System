package com.taskos.knowledge.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.knowledge.service.KnowledgeDocumentException;
import com.taskos.knowledge.service.KnowledgeDocumentService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/knowledge")
public class KnowledgeLibraryController {
    private final KnowledgeDocumentService documents;

    public KnowledgeLibraryController(KnowledgeDocumentService documents) { this.documents = documents; }

    @GetMapping
    public String index(@RequestParam(defaultValue = "") String q, Authentication authentication, Model model) {
        UUID ownerId = actor(authentication);
        model.addAttribute("activeNav", "knowledge");
        model.addAttribute("query", q);
        model.addAttribute("documents", documents.library(ownerId));
        try {
            model.addAttribute("results", documents.search(ownerId, q));
        } catch (KnowledgeDocumentException ex) {
            model.addAttribute("results", java.util.List.of());
            model.addAttribute("knowledgeError", ex.getMessage());
        }
        return "knowledge/library";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file, Authentication authentication, RedirectAttributes redirect) {
        if (file == null || file.isEmpty()) {
            redirect.addFlashAttribute("knowledgeError", "Choose a document before uploading.");
            return "redirect:/knowledge";
        }
        try {
            var document = documents.upload(actor(authentication), file.getOriginalFilename(), file.getBytes());
            if ("INDEXED".equals(document.getIndexState().name()))
                redirect.addFlashAttribute("knowledgeNotice", document.getFileName() + " is indexed and ready to search.");
            else redirect.addFlashAttribute("knowledgeError", document.getFileName() + " was stored, but indexing failed: " + document.getFailureMessage());
        } catch (KnowledgeDocumentException | IOException ex) {
            redirect.addFlashAttribute("knowledgeError", ex instanceof KnowledgeDocumentException
                    ? ex.getMessage() : "TASK OS could not read the uploaded file.");
        }
        return "redirect:/knowledge";
    }

    @PostMapping("/{id}/retry")
    public String retry(@PathVariable UUID id, Authentication authentication, RedirectAttributes redirect) {
        try {
            var document = documents.retry(actor(authentication), id);
            redirect.addFlashAttribute(document.getIndexState().name().equals("INDEXED") ? "knowledgeNotice" : "knowledgeError",
                    document.getIndexState().name().equals("INDEXED") ? "Indexing completed." : document.getFailureMessage());
        } catch (KnowledgeDocumentException ex) {
            redirect.addFlashAttribute("knowledgeError", ex.getMessage());
        }
        return "redirect:/knowledge";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, Authentication authentication, RedirectAttributes redirect) {
        try {
            documents.delete(actor(authentication), id);
            redirect.addFlashAttribute("knowledgeNotice", "Document version deleted.");
        } catch (KnowledgeDocumentException ex) {
            redirect.addFlashAttribute("knowledgeError", ex.getMessage());
        }
        return "redirect:/knowledge";
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, Authentication authentication) {
        var file = documents.load(actor(authentication), id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }

    private UUID actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof TaskOsUserDetails user) return user.getId();
        throw new AccessDeniedException("A TASK OS account is required.");
    }
}
