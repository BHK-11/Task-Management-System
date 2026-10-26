package com.taskos.task.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class TaskSearchForm {
    @Size(max = 120, message = "Search must be 120 characters or fewer.")
    private String q = "";

    @Pattern(regexp = "^(|TODO|IN_PROGRESS|ON_HOLD|COMPLETED|CANCELLED)$")
    private String status = "";

    @Pattern(regexp = "^(|LOW|MEDIUM|HIGH|URGENT)$")
    private String priority = "";

    @Pattern(regexp = "^(ACTIVE|ARCHIVED)$")
    private String view = "ACTIVE";

    @Pattern(regexp = "^(ALL|OVERDUE|TODAY|WEEK|NO_DATE)$")
    private String due = "ALL";

    @Pattern(regexp = "^(ALL|YES|NO)$")
    private String subtasks = "ALL";

    private UUID tagId;

    @Pattern(regexp = "^(UPDATED|CREATED_DESC|CREATED_ASC|DUE_ASC|DUE_DESC|PRIORITY|TITLE|COMPLETED)$")
    private String sort = "UPDATED";

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q == null ? "" : q; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status == null ? "" : status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority == null ? "" : priority; }
    public String getView() { return view; }
    public void setView(String view) { this.view = view; }
    public String getDue() { return due; }
    public void setDue(String due) { this.due = due; }
    public String getSubtasks() { return subtasks; }
    public void setSubtasks(String subtasks) { this.subtasks = subtasks; }
    public UUID getTagId() { return tagId; }
    public void setTagId(UUID tagId) { this.tagId = tagId; }
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
}
