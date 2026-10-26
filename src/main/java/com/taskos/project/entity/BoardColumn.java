package com.taskos.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "project_board_columns", schema = "task_os")
public class BoardColumn {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "project_id", nullable = false, updatable = false) private UUID projectId;
    @Column(nullable = false, length = 48) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private BoardStage stage;
    @Column(nullable = false) private int position;
    @Column(name = "wip_limit") private Integer wipLimit;
    @Enumerated(EnumType.STRING) @Column(name = "wip_policy", nullable = false, length = 12) private WipPolicy wipPolicy;
    @Version private Long version;

    protected BoardColumn() { }

    public static BoardColumn create(UUID ownerId, UUID projectId, String name, BoardStage stage,
            int position, Integer wipLimit, WipPolicy wipPolicy) {
        BoardColumn column = new BoardColumn();
        column.id = UUID.randomUUID(); column.ownerId = ownerId; column.projectId = projectId;
        column.name = name.trim(); column.stage = stage; column.position = position;
        column.wipLimit = wipLimit; column.wipPolicy = wipPolicy == null ? WipPolicy.NONE : wipPolicy;
        return column;
    }

    public void update(String name, BoardStage stage, Integer wipLimit, WipPolicy wipPolicy) {
        this.name = name.trim(); this.stage = stage; this.wipLimit = wipLimit;
        this.wipPolicy = wipPolicy == null ? WipPolicy.NONE : wipPolicy;
    }

    public void move(int position) { this.position = position; }
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getProjectId() { return projectId; }
    public String getName() { return name; }
    public BoardStage getStage() { return stage; }
    public int getPosition() { return position; }
    public Integer getWipLimit() { return wipLimit; }
    public WipPolicy getWipPolicy() { return wipPolicy; }
    public long getVersion() { return version == null ? 0 : version; }
}
