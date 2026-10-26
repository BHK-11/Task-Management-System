package com.taskos.productivity.entity;

import com.taskos.task.entity.Task;
import com.taskos.user.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gtd_inbox_items", schema = "task_os")
public class GtdInboxItem {
    @Id private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private UserAccount owner;

    @Column(name = "capture_text", nullable = false, length = 500)
    private String captureText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InboxState state = InboxState.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_task_id")
    private Task convertedTask;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    @Column(name = "clarified_at")
    private Instant clarifiedAt;

    @Version private Long version;

    protected GtdInboxItem() { }

    public static GtdInboxItem capture(UserAccount owner, String text, Instant now) {
        GtdInboxItem item = new GtdInboxItem();
        item.id = UUID.randomUUID();
        item.owner = owner;
        item.captureText = text.trim();
        item.state = InboxState.OPEN;
        item.capturedAt = now;
        return item;
    }

    public void clarify(InboxState next, Task task, Instant now) {
        if (state != InboxState.OPEN) throw new IllegalStateException("This capture has already been clarified.");
        if (next == InboxState.OPEN) throw new IllegalArgumentException("Choose a clarification outcome.");
        if (next == InboxState.CONVERTED && task == null) throw new IllegalArgumentException("A converted capture needs its task.");
        state = next;
        convertedTask = task;
        clarifiedAt = now;
    }

    public void reopen(Instant now) {
        if (state != InboxState.SOMEDAY && state != InboxState.REFERENCE)
            throw new IllegalStateException("Only Someday and Reference captures can be returned to the inbox.");
        state = InboxState.OPEN;
        clarifiedAt = null;
    }

    public UUID getId() { return id; }
    public UserAccount getOwner() { return owner; }
    public String getCaptureText() { return captureText; }
    public InboxState getState() { return state; }
    public Task getConvertedTask() { return convertedTask; }
    public Instant getCapturedAt() { return capturedAt; }
    public Instant getClarifiedAt() { return clarifiedAt; }
}
