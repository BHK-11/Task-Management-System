package com.taskos.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AiModelPreferencesForm {
    @NotBlank
    @Pattern(regexp = "AUTO|FAST|BALANCED|QUALITY|ECONOMICAL|PRIVATE|MANUAL")
    private String mode = "AUTO";

    @Size(max = 200)
    private String target = "";

    private boolean allowCloudProcessing = true;
    private boolean allowFallback;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public boolean isAllowCloudProcessing() { return allowCloudProcessing; }
    public void setAllowCloudProcessing(boolean allowCloudProcessing) { this.allowCloudProcessing = allowCloudProcessing; }
    public boolean isAllowFallback() { return allowFallback; }
    public void setAllowFallback(boolean allowFallback) { this.allowFallback = allowFallback; }
}
