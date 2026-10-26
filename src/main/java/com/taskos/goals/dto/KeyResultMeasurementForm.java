package com.taskos.goals.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public class KeyResultMeasurementForm {
    @NotNull @Digits(integer = 12, fraction = 4) @DecimalMin("-999999999999.9999") @DecimalMax("999999999999.9999") private BigDecimal value;
    @NotNull private LocalDate date = LocalDate.now();
    @Size(max = 1000) private String notes = "";
    public BigDecimal getValue() { return value; } public void setValue(BigDecimal v) { value = v; }
    public LocalDate getDate() { return date; } public void setDate(LocalDate v) { date = v; }
    public String getNotes() { return notes; } public void setNotes(String v) { notes = v; }
}
