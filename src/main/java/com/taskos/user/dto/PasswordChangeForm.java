package com.taskos.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordChangeForm {
    @NotBlank(message = "Enter your current password.")
    @Size(max = 256, message = "Use 256 characters or fewer.")
    private String currentPassword;

    @NotBlank(message = "Enter a new password.")
    @Size(min = 12, max = 256, message = "Use 12 to 256 characters. A long passphrase is welcome.")
    private String newPassword;

    @NotBlank(message = "Confirm your new password.")
    @Size(max = 256, message = "Use 256 characters or fewer.")
    private String confirmPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
