package com.taskos.agile.dto;

import com.taskos.agile.entity.AcceptanceCriterion;
import com.taskos.agile.entity.AgileWorkItem;
import com.taskos.agile.entity.Epic;
import com.taskos.agile.entity.DefinitionOfDoneCriterion;
import com.taskos.agile.entity.Sprint;
import com.taskos.project.entity.BoardColumn;
import com.taskos.task.entity.Task;
import java.util.List;

public record AgileBacklogItem(Task task, AgileWorkItem agile, Epic epic, Sprint sprint,
        List<AcceptanceCriterion> criteria, List<DefinitionOfDoneCriterion> definitionOfDone,
        BoardColumn column) { }
