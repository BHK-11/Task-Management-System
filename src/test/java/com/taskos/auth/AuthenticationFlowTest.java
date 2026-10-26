package com.taskos.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.taskos.auth.entity.AuthEventType;
import com.taskos.auth.repository.AuthEventRepository;
import com.taskos.auth.service.RegistrationService;
import com.taskos.security.service.DatabaseUserDetailsService;
import com.taskos.security.service.LoginAttemptLimiter;
import com.taskos.security.service.LoginRateLimitFilter;
import com.taskos.user.entity.UserAccount;
import com.taskos.user.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthenticationFlowTest {
    private static final String INITIAL_PASSWORD = "correct horse battery staple 27";

    @Autowired private MockMvc mvc;
    @Autowired private UserAccountRepository users;
    @Autowired private RegistrationService registrationService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseUserDetailsService userDetailsService;
    @Autowired private AuthEventRepository events;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private LoginAttemptLimiter loginAttemptLimiter;
    @Autowired private FilterChainProxy filterChainProxy;

    @Test
    void flywayCreatesIdentitySchemaAndOnlyTheExpectedGlobalRoles() {
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '2' and success = true", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from task_os.roles", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from task_os.roles where role_name = 'ROLE_USER'", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from task_os.roles where role_name = 'ROLE_ADMIN'", Integer.class));
    }

    @Test
    void registrationNormalizesEmailHashesPasswordAndIgnoresRequestedRole() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/signup").with(csrf())
                        .param("displayName", "  Ada   Misra ")
                        .param("email", email.toUpperCase())
                        .param("password", INITIAL_PASSWORD)
                        .param("confirmPassword", INITIAL_PASSWORD)
                        .param("role", "ROLE_ADMIN")
                        .param("roles[0]", "ROLE_ADMIN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        UserAccount account = users.findByEmail(email).orElseThrow();
        assertEquals("Ada Misra", account.getDisplayName());
        assertTrue(account.getPasswordHash().startsWith("{pbkdf2}"));
        assertTrue(passwordEncoder.matches(INITIAL_PASSWORD, account.getPasswordHash()));
        assertFalse(account.getPasswordHash().contains(INITIAL_PASSWORD));
        assertEquals(1, account.getRoles().size());
        assertEquals("ROLE_USER", account.getRoles().iterator().next().getName());
        assertTrue(events.findAll().stream().anyMatch(event -> event.getEventType() == AuthEventType.ACCOUNT_REGISTERED));

        var principal = userDetailsService.loadUserByUsername("  " + email.toUpperCase() + " ");
        assertEquals(email, principal.getUsername());
        assertEquals("ROLE_USER", principal.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void signupValidationRejectsMissingFieldsPasswordMismatchAndCaseVariantDuplicate() throws Exception {
        mvc.perform(post("/signup").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Enter your name.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Create a password.")));

        String email = uniqueEmail();
        mvc.perform(post("/signup").with(csrf())
                        .param("displayName", "Test Person")
                        .param("email", email)
                        .param("password", INITIAL_PASSWORD)
                        .param("confirmPassword", "a different long passphrase"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("The passwords do not match.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(INITIAL_PASSWORD))));

        mvc.perform(post("/signup").with(csrf())
                        .param("displayName", "Test Person")
                        .param("email", "not-an-email")
                        .param("password", "short")
                        .param("confirmPassword", "short"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Enter a valid email address.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Use 12 to 256 characters.")));

        registrationService.register("Test Person", email, INITIAL_PASSWORD);
        mvc.perform(post("/signup").with(csrf())
                        .param("displayName", "Another Person")
                        .param("email", email.toUpperCase())
                        .param("password", INITIAL_PASSWORD)
                        .param("confirmPassword", INITIAL_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("An account with this email already exists.")));
        assertEquals(1, users.count());
    }

    @Test
    void csrfProtectsRegistrationAndAccountChanges() throws Exception {
        mvc.perform(post("/signup")
                        .param("displayName", "Test Person")
                        .param("email", uniqueEmail())
                        .param("password", INITIAL_PASSWORD)
                        .param("confirmPassword", INITIAL_PASSWORD))
                .andExpect(status().isForbidden());
        assertEquals(0, users.count());
    }

    @Test
    void successfulLoginProvidesProtectedProfileThenLogoutEndsSession() throws Exception {
        String email = uniqueEmail();
        registrationService.register("Sam User", email, INITIAL_PASSWORD);
        MvcResult loginPage = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        MockHttpSession anonymousSession = (MockHttpSession) loginPage.getRequest().getSession(false);
        if (anonymousSession == null) anonymousSession = new MockHttpSession();
        String oldSessionId = anonymousSession.getId();

        MvcResult login = mvc.perform(post("/login").session(anonymousSession).with(csrf())
                        .param("email", email)
                        .param("password", INITIAL_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn();
        MockHttpSession authenticatedSession = (MockHttpSession) login.getRequest().getSession(false);
        assertTrue(authenticatedSession != null);
        assertNotEquals(oldSessionId, authenticatedSession.getId(), "login should rotate the session identifier");

        mvc.perform(get("/dashboard").session(authenticatedSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PERSONAL COMMAND CENTER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sam User")));
        mvc.perform(get("/profile").session(authenticatedSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(email)));
        mvc.perform(post("/profile").session(authenticatedSession)
                        .param("displayName", "Should Not Save"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/logout").session(authenticatedSession).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
        mvc.perform(get("/profile")).andExpect(status().is3xxRedirection());
        assertTrue(authenticatedSession.isInvalid());
    }

    @Test
    void concurrentSessionsAreBoundedAndOldestSessionExpires() throws Exception {
        String email = uniqueEmail();
        registrationService.register("Session Person", email, INITIAL_PASSWORD);
        MockHttpSession first = login(email, INITIAL_PASSWORD);
        login(email, INITIAL_PASSWORD);
        login(email, INITIAL_PASSWORD);
        login(email, INITIAL_PASSWORD);

        mvc.perform(get("/profile").session(first))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("This session has been expired")));
        assertTrue(first.isInvalid());
    }

    @Test
    void wrongCredentialsUseGenericFailureAndDisabledUsersCannotSignIn() throws Exception {
        String email = uniqueEmail();
        UserAccount account = registrationService.register("Test Person", email, INITIAL_PASSWORD);
        mvc.perform(post("/login").with(csrf()).param("email", email).param("password", "wrong password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").with(csrf()).param("email", uniqueEmail()).param("password", "wrong password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Email or password was incorrect.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(email))));

        jdbc.update("update task_os.users set account_status = 'DISABLED' where id = ?", account.getId());
        entityManager.clear();
        mvc.perform(post("/login").with(csrf()).param("email", email).param("password", INITIAL_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
        assertTrue(events.findAll().stream().anyMatch(event -> event.getEventType() == AuthEventType.LOGIN_FAILED));
    }

    @Test
    void repeatedFailuresForOneAddressAndAccountAreTemporarilyThrottled() throws Exception {
        String email = uniqueEmail();
        assertTrue(filterChainProxy.getFilterChains().stream().flatMap(chain -> chain.getFilters().stream())
                .anyMatch(filter -> filter instanceof LoginRateLimitFilter));
        for (int attempt = 0; attempt < 10; attempt++) {
            mvc.perform(post("/login").with(csrf()).param("email", email).param("password", "incorrect passphrase"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?error"));
        }
        assertTrue(loginAttemptLimiter.isBlocked("127.0.0.1", email));
        mvc.perform(post("/login").with(csrf()).param("email", email).param("password", INITIAL_PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Retry-After", "900"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Too many sign-in attempts")));
    }

    @Test
    void profileChangesUseAuthenticatedIdentityAndRenderUntrustedTextSafely() throws Exception {
        String email = uniqueEmail();
        UserAccount current = registrationService.register("Current Person", email, INITIAL_PASSWORD);
        UserAccount other = registrationService.register("Other Person", uniqueEmail(), INITIAL_PASSWORD);
        MockHttpSession session = login(email, INITIAL_PASSWORD);

        mvc.perform(post("/profile").session(session).with(csrf())
                        .param("displayName", "<script>alert(1)</script>")
                        .param("userId", other.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile?updated"));
        mvc.perform(get("/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>alert(1)</script>"))));
        assertEquals("<script>alert(1)</script>", users.findById(current.getId()).orElseThrow().getDisplayName());
        assertEquals("Other Person", users.findById(other.getId()).orElseThrow().getDisplayName());
    }

    @Test
    void savedPreferencesAndPasswordChangeApplyToCurrentAccountAndExpireSessions() throws Exception {
        String email = uniqueEmail();
        UserAccount account = registrationService.register("Preferences Person", email, INITIAL_PASSWORD);
        MockHttpSession session = login(email, INITIAL_PASSWORD);

        mvc.perform(post("/settings/account").session(session).with(csrf())
                        .param("displayName", "Preferences Person")
                        .param("themePreference", "DARK")
                        .param("timezone", "Asia/Kolkata")
                        .param("locale", "en"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings/account?updated"));
        var profile = users.findById(account.getId()).orElseThrow();
        assertEquals("DARK", profile.getThemePreference().name());
        assertEquals("Asia/Kolkata", profile.getTimezone());

        mvc.perform(post("/settings/security/password").session(session).with(csrf())
                        .param("currentPassword", "not the current password")
                        .param("newPassword", "a very different secure passphrase 77")
                        .param("confirmPassword", "a very different secure passphrase 77"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("The current password was incorrect.")));

        String newPassword = "a very different secure passphrase 77";
        mvc.perform(post("/settings/security/password").session(session).with(csrf())
                        .param("currentPassword", INITIAL_PASSWORD)
                        .param("newPassword", newPassword)
                        .param("confirmPassword", newPassword))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?passwordChanged"));
        assertTrue(session.isInvalid());
        assertTrue(passwordEncoder.matches(newPassword,
                users.findById(account.getId()).orElseThrow().getPasswordHash()));
        assertTrue(events.findAll().stream().anyMatch(event -> event.getEventType() == AuthEventType.PASSWORD_CHANGED));
        mvc.perform(post("/login").with(csrf()).param("email", email).param("password", newPassword))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/login").with(csrf())
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }
}
