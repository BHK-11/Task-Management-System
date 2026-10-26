package com.taskos.habits.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public class HabitCheckInForm {
    @NotNull private LocalDate date;
    @Digits(integer = 12, fraction = 4) @DecimalMin("0") private BigDecimal quantity;
    private boolean performed = true;
    @Size(max = 600) private String notes = "";
    public LocalDate getDate() { return date; } public void setDate(LocalDate v) { date = v; }
    public BigDecimal getQuantity() { return quantity; } public void setQuantity(BigDecimal v) { quantity = v; }
    public boolean isPerformed() { return performed; } public void setPerformed(boolean v) { performed = v; }
    public String getNotes() { return notes; } public void setNotes(String v) { notes = v; }
}
