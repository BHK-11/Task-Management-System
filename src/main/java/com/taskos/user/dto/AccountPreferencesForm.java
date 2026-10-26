package com.taskos.user.dto;

import com.taskos.user.entity.ThemePreference;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AccountPreferencesForm {
    @NotBlank(message = "Enter a display name.")
    @Size(max = 80, message = "Use 80 characters or fewer.")
    private String displayName;

    @NotNull(message = "Choose a theme preference.")
    private ThemePreference themePreference = ThemePreference.SYSTEM;

    @NotBlank(message = "Choose a timezone.")
    @Size(max = 64)
    private String timezone = "UTC";

    @NotBlank
    @Pattern(regexp = "en", message = "English is the only language currently available.")
    private String locale = "en";

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public ThemePreference getThemePreference() { return themePreference; }
    public void setThemePreference(ThemePreference themePreference) { this.themePreference = themePreference; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
}
