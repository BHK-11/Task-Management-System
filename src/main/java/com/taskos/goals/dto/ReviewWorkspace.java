package com.taskos.goals.dto;

import com.taskos.goals.entity.Review;
import java.util.List;

public record ReviewWorkspace(Review review, ReviewForm form, long completedTasks, long habitCheckIns,
        long goalCheckIns, long keyResultCheckIns, long focusSeconds, List<Review> history) { }
