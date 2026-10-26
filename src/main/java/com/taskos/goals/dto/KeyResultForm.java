package com.taskos.goals.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class KeyResultForm {
    @NotBlank @Size(max = 160) private String title;
    @Size(max = 40) private String unit = "";
    @NotNull @Digits(integer = 12, fraction = 4) @DecimalMin("-999999999999.9999") @DecimalMax("999999999999.9999") private BigDecimal baseline;
    @NotNull @Digits(integer = 12, fraction = 4) @DecimalMin("-999999999999.9999") @DecimalMax("999999999999.9999") private BigDecimal target;
    @NotBlank private String direction = "INCREASE";
    public String getTitle() { return title; } public void setTitle(String v) { title = v; }
    public String getUnit() { return unit; } public void setUnit(String v) { unit = v; }
    public BigDecimal getBaseline() { return baseline; } public void setBaseline(BigDecimal v) { baseline = v; }
    public BigDecimal getTarget() { return target; } public void setTarget(BigDecimal v) { target = v; }
    public String getDirection() { return direction; } public void setDirection(String v) { direction = v; }
}
