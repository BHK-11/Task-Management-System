package com.taskos.time.dto;

import com.taskos.time.entity.FocusMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class FocusStartForm {
    private UUID taskId;
    @NotNull private FocusMode mode = FocusMode.POMODORO;
    @Min(1) @Max(240) private int focusMinutes = 25;
    @Min(1) @Max(60) private int shortBreakMinutes = 5;
    @Min(1) @Max(120) private int longBreakMinutes = 15;
    @Size(max = 1200) private String notes = "";
    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }
    public FocusMode getMode() { return mode; }
    public void setMode(FocusMode mode) { this.mode = mode; }
    public int getFocusMinutes() { return focusMinutes; }
    public void setFocusMinutes(int focusMinutes) { this.focusMinutes = focusMinutes; }
    public int getShortBreakMinutes() { return shortBreakMinutes; }
    public void setShortBreakMinutes(int shortBreakMinutes) { this.shortBreakMinutes = shortBreakMinutes; }
    public int getLongBreakMinutes() { return longBreakMinutes; }
    public void setLongBreakMinutes(int longBreakMinutes) { this.longBreakMinutes = longBreakMinutes; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
