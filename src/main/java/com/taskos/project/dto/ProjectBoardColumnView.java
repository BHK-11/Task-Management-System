package com.taskos.project.dto;

import com.taskos.project.entity.BoardColumn;
import com.taskos.project.entity.ProjectTaskPlacement;
import java.util.List;

public record ProjectBoardColumnView(BoardColumn column, List<ProjectTaskPlacement> cards,
        long wipCount, boolean overWipLimit) { }
