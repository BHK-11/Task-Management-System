package com.taskos.web;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.collaboration.service.WorkspaceService;
import com.taskos.user.dto.ProfileView;
import com.taskos.user.service.UserProfileService;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class AuthenticatedModelAdvice {
    private final UserProfileService profiles;
    private final WorkspaceService workspaces;

    public AuthenticatedModelAdvice(UserProfileService profiles, WorkspaceService workspaces) {
        this.profiles = profiles;
        this.workspaces = workspaces;
    }

    @ModelAttribute
    public void addAuthenticatedUser(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof TaskOsUserDetails user) {
            ProfileView profile = profiles.profile(user.getId());
            model.addAttribute("currentUser", profile);
            model.addAttribute("accountTheme", profile.themePreference().name().toLowerCase());
            model.addAttribute("workspaceOptions", workspaces.switcherOptions(user.getId()));
        } else {
            model.addAttribute("currentUser", null);
            model.addAttribute("accountTheme", "");
            model.addAttribute("workspaceOptions", java.util.List.of());
        }
    }
}
