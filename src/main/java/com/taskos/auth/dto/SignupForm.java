package com.taskos.auth.dto;

import com.taskos.common.EmailAddress;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SignupForm {
    @NotBlank(message = "Enter your name.")
    @Size(max = 80, message = "Use 80 characters or fewer.")
    private String displayName;

    @NotBlank(message = "Enter your email address.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 254, message = "Email addresses must be 254 characters or fewer.")
    private String email;

    @NotBlank(message = "Create a password.")
    @Size(min = 12, max = 256, message = "Use 12 to 256 characters. A long passphrase is welcome.")
    private String password;

    @NotBlank(message = "Confirm your password.")
    @Size(max = 256, message = "Use 256 characters or fewer.")
    private String confirmPassword;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = EmailAddress.canonicalize(email); }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
