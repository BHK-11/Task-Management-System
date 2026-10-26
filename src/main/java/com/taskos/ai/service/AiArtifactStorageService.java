package com.taskos.ai.service;

import com.taskos.ai.entity.AiArtifact;
import com.taskos.ai.entity.AiPlan;
import com.taskos.ai.entity.AiPlanStep;
import com.taskos.ai.repository.AiArtifactRepository;
import com.taskos.user.repository.UserAccountRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AiArtifactStorageService {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private final Path root;
    private final AiArtifactRepository artifacts;
    private final UserAccountRepository users;
    private final Clock clock;

    public AiArtifactStorageService(@Value("${taskos.ai.artifact-directory:${user.home}/.taskos/artifacts}") String root,
            AiArtifactRepository artifacts, UserAccountRepository users, Clock clock) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.artifacts = artifacts;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public AiArtifact store(UUID ownerId, AiPlan plan, AiPlanStep step, String fileName, String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES || fileName == null || fileName.length() > 160)
            throw new AiCopilotException("The generated file was empty or exceeded the 10 MB limit.");
        var owner = users.findById(ownerId).orElseThrow(() -> new AiCopilotException("Your account is unavailable."));
        String digest = sha256(bytes);
        AiArtifact artifact = AiArtifact.create(owner, plan, step, fileName, contentType, bytes.length, digest, clock.instant());
        Path directory = root.resolve(ownerId.toString()).normalize();
        Path destination = directory.resolve(artifact.getStorageKey()).normalize();
        if (!directory.startsWith(root) || !destination.startsWith(directory))
            throw new AiCopilotException("The artifact storage location is invalid.");
        try {
            Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory)) throw new AiCopilotException("The artifact storage location is invalid.");
            setPrivate(directory, true);
            Path temporary = Files.createTempFile(directory, ".artifact-", ".tmp");
            try {
                setPrivate(temporary, false);
                Files.write(temporary, bytes);
                try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(temporary, destination); }
            } finally { Files.deleteIfExists(temporary); }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try { Files.deleteIfExists(destination); } catch (IOException ignored) { }
                    }
                }
            });
            return artifacts.saveAndFlush(artifact);
        } catch (IOException ex) {
            throw new AiCopilotException("TASK OS could not safely store the generated file.", ex);
        }
    }

    @Transactional(readOnly = true)
    public Download load(UUID ownerId, UUID artifactId) {
        AiArtifact artifact = artifacts.findByIdAndOwner_Id(artifactId, ownerId)
                .orElseThrow(AiArtifactNotFoundException::new);
        Path ownerDirectory = root.resolve(ownerId.toString()).normalize();
        Path file = ownerDirectory.resolve(artifact.getStorageKey()).normalize();
        if (!file.startsWith(ownerDirectory) || Files.isSymbolicLink(ownerDirectory) || Files.isSymbolicLink(file))
            throw new AiCopilotException("The deliverable location is invalid.");
        try {
            if (Files.size(file) != artifact.getSize() || artifact.getSize() > MAX_BYTES)
                throw new AiCopilotException("The deliverable failed its integrity check.");
            byte[] bytes;
            try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_BYTES + 1); }
            if (bytes.length > MAX_BYTES || bytes.length != artifact.getSize() || !sha256(bytes).equals(artifact.getSha256()))
                throw new AiCopilotException("The deliverable failed its integrity check.");
            return new Download(artifact.getFileName(), artifact.getContentType(), bytes);
        } catch (IOException ex) {
            throw new AiCopilotException("The verified deliverable file is unavailable.", ex);
        }
    }

    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private void setPrivate(Path path, boolean directory) throws IOException {
        try {
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString(
                    directory ? "rwx------" : "rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Windows ACLs remain governed by the TASK OS process account.
        }
    }

    public record Download(String fileName, String contentType, byte[] bytes) {}
}
