package com.taskos.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DisplayNameForm {
    @NotBlank(message = "Enter a display name.")
    @Size(max = 80, message = "Use 80 characters or fewer.")
    private String displayName;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
