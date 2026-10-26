package com.taskos.agile.dto;

import com.taskos.project.entity.BoardColumn;
import java.util.List;

public record SprintBoardColumn(BoardColumn column, List<AgileBacklogItem> cards) { }
