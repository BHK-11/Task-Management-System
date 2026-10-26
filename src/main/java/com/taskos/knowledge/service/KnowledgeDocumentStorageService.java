package com.taskos.knowledge.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class KnowledgeDocumentStorageService {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private final Path root;

    public KnowledgeDocumentStorageService(@Value("${taskos.knowledge.directory:${user.home}/.taskos/knowledge}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public void store(UUID ownerId, String storageKey, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES)
            throw new KnowledgeDocumentException("The document is empty or exceeds the 10 MB limit.");
        Path directory = ownerDirectory(ownerId);
        Path destination = file(ownerId, storageKey);
        try {
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) throw new KnowledgeDocumentException("The document storage location is invalid.");
            Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory)) throw new KnowledgeDocumentException("The document storage location is invalid.");
            setPrivate(directory, true);
            Path temporary = Files.createTempFile(directory, ".knowledge-", ".tmp");
            try {
                setPrivate(temporary, false);
                Files.write(temporary, bytes);
                try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(temporary, destination); }
            } finally { Files.deleteIfExists(temporary); }
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) deleteFile(destination);
                    }
                });
            }
        } catch (IOException ex) {
            throw new KnowledgeDocumentException("TASK OS could not safely store the document.", ex);
        }
    }

    public Download load(UUID ownerId, String storageKey, long size, String sha256) {
        Path directory = ownerDirectory(ownerId);
        Path file = file(ownerId, storageKey);
        if (Files.isSymbolicLink(root) || Files.isSymbolicLink(directory) || Files.isSymbolicLink(file))
            throw new KnowledgeDocumentException("The document location is invalid.");
        try {
            if (!Files.isRegularFile(file) || size <= 0 || size > MAX_BYTES || Files.size(file) != size)
                throw new KnowledgeDocumentException("The document failed its integrity check.");
            byte[] bytes;
            try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_BYTES + 1); }
            if (bytes.length != size || !digest(bytes).equals(sha256))
                throw new KnowledgeDocumentException("The document failed its integrity check.");
            return new Download(bytes);
        } catch (IOException ex) {
            throw new KnowledgeDocumentException("The verified document is unavailable.", ex);
        }
    }

    public void deleteAfterCommit(UUID ownerId, String storageKey) {
        Path file = file(ownerId, storageKey);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { deleteFile(file); }
            });
        } else deleteFile(file);
    }

    public static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private Path ownerDirectory(UUID ownerId) {
        Path directory = root.resolve(ownerId.toString()).normalize();
        if (!directory.startsWith(root)) throw new KnowledgeDocumentException("The document storage location is invalid.");
        return directory;
    }

    private Path file(UUID ownerId, String key) {
        UUID id;
        try { id = UUID.fromString(key); }
        catch (RuntimeException ex) { throw new KnowledgeDocumentException("The document location is invalid."); }
        Path directory = ownerDirectory(ownerId);
        Path target = directory.resolve(id.toString()).normalize();
        if (!target.startsWith(directory)) throw new KnowledgeDocumentException("The document location is invalid.");
        return target;
    }

    private static void setPrivate(Path path, boolean directory) throws IOException {
        try {
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString(
                    directory ? "rwx------" : "rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Windows ACLs remain governed by the TASK OS process account.
        }
    }

    private static void deleteFile(Path file) {
        try { Files.deleteIfExists(file); } catch (IOException ignored) { }
    }

    public record Download(byte[] bytes) {}
}
