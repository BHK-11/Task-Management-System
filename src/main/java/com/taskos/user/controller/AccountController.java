package com.taskos.user.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.user.dto.AccountPreferencesForm;
import com.taskos.user.dto.DisplayNameForm;
import com.taskos.user.dto.PasswordChangeForm;
import com.taskos.user.dto.ProfileView;
import com.taskos.user.service.UserProfileService;
import java.util.Objects;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class AccountController {
    private final UserProfileService profiles;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
    private final CookieClearingLogoutHandler cookieClearingLogoutHandler = new CookieClearingLogoutHandler("JSESSIONID");

    public AccountController(UserProfileService profiles) {
        this.profiles = profiles;
        logoutHandler.setInvalidateHttpSession(true);
        logoutHandler.setClearAuthentication(true);
    }

    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model) {
        ProfileView profile = profiles.profile(currentUser(authentication).getId());
        DisplayNameForm form = new DisplayNameForm();
        form.setDisplayName(profile.displayName());
        model.addAttribute("profile", profile);
        model.addAttribute("displayNameForm", form);
        return "account/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(Authentication authentication,
            @Valid @ModelAttribute("displayNameForm") DisplayNameForm form,
            BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("profile", profiles.profile(currentUser(authentication).getId()));
            return "account/profile";
        }
        profiles.updateDisplayName(currentUser(authentication).getId(), form.getDisplayName());
        return "redirect:/profile?updated";
    }

    @GetMapping("/settings/account")
    public String accountSettings(Authentication authentication, Model model) {
        ProfileView profile = profiles.profile(currentUser(authentication).getId());
        AccountPreferencesForm form = new AccountPreferencesForm();
        form.setDisplayName(profile.displayName());
        form.setThemePreference(profile.themePreference());
        form.setTimezone(profile.timezone());
        form.setLocale(profile.locale());
        model.addAttribute("profile", profile);
        model.addAttribute("preferencesForm", form);
        return "account/settings";
    }

    @PostMapping("/settings/account")
    public String updateAccountSettings(Authentication authentication,
            @Valid @ModelAttribute("preferencesForm") AccountPreferencesForm form,
            BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("profile", profiles.profile(currentUser(authentication).getId()));
            return "account/settings";
        }
        try {
            profiles.updatePreferences(currentUser(authentication).getId(), form.getDisplayName(), form.getThemePreference(),
                    form.getTimezone(), form.getLocale());
        } catch (IllegalArgumentException ex) {
            bindingResult.rejectValue("timezone", "timezone.unsupported", ex.getMessage());
            model.addAttribute("profile", profiles.profile(currentUser(authentication).getId()));
            return "account/settings";
        }
        return "redirect:/settings/account?updated";
    }

    @GetMapping("/settings/security")
    public String securitySettings(Model model) {
        model.addAttribute("passwordChangeForm", new PasswordChangeForm());
        return "account/security";
    }

    @PostMapping("/settings/security/password")
    public String changePassword(Authentication authentication,
            @Valid @ModelAttribute("passwordChangeForm") PasswordChangeForm form,
            BindingResult bindingResult, Model model, HttpServletRequest request,
            HttpServletResponse response) {
        if (!Objects.equals(form.getNewPassword(), form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "The passwords do not match.");
        }
        if (bindingResult.hasErrors()) return "account/security";
        try {
            profiles.changePassword(currentUser(authentication).getId(), form.getCurrentPassword(), form.getNewPassword());
        } catch (UserProfileService.InvalidCurrentPasswordException ex) {
            bindingResult.rejectValue("currentPassword", "password.current.invalid", "The current password was incorrect.");
            return "account/security";
        }
        logoutHandler.logout(request, response, authentication);
        cookieClearingLogoutHandler.logout(request, response, authentication);
        SecurityContextHolder.clearContext();
        return "redirect:/login?passwordChanged";
    }

    private TaskOsUserDetails currentUser(Authentication authentication) {
        return (TaskOsUserDetails) authentication.getPrincipal();
    }
}
