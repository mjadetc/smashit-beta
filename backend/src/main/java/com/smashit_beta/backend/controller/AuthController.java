package com.smashit_beta.backend.controller;

import com.smashit_beta.backend.dto.request.LoginRequest;
import com.smashit_beta.backend.dto.request.RegisterRequest;
import com.smashit_beta.backend.dto.response.UserResponse;
import com.smashit_beta.backend.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController = this class handles HTTP requests, and whatever the methods
// return is converted to JSON automatically.
// @RequestMapping = every URL in this class starts with /api/auth.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    // Spring's helper for reading/writing the "who is logged in" info for the current request.
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    // Constructor injection: Spring sees this constructor and passes in the beans
    // (objects it manages) automatically. No "new UserService(...)" needed.
    public AuthController(
            UserService userService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository
    ) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    // GET /api/auth/csrf
    // The React app calls this once when it starts. Asking for the token forces
    // Spring Security to send the XSRF-TOKEN cookie. React then copies that cookie's
    // value into an "X-XSRF-TOKEN" header on every POST/PUT/PATCH/DELETE.
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        // Reading the token is what makes Spring create and send the cookie.
        csrfToken.getToken();
        // 204 No Content: success, nothing in the body.
        return ResponseEntity.noContent().build();
    }

    // POST /api/auth/register
    // @Valid runs the rules in RegisterRequest (@NotBlank, @Email...) BEFORE this method runs.
    // If they fail, GlobalExceptionHandler returns 400 and this method never runs.
    // @RequestBody = build the RegisterRequest from the JSON body.
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse createdUser = userService.register(request);

        // 201 Created is the standard status for "a new resource was made".
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    // POST /api/auth/login
    // HttpServletRequest/Response are the raw HTTP request/response; Spring passes them in
    // because we need them to store the login in the session.
    @PostMapping("/login")
    public UserResponse login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // 1. Wrap the submitted credentials in an "unauthenticated" token.
        Authentication unauthenticated = UsernamePasswordAuthenticationToken.unauthenticated(
                loginRequest.email().trim(),
                loginRequest.password()
        );

        // 2. Ask Spring Security to check them. Behind the scenes it calls
        //    UserDetailsServiceImpl.loadUserByUsername(email) and compares the
        //    password with the BCrypt hash. Wrong email OR wrong password throws
        //    BadCredentialsException -> GlobalExceptionHandler returns 401.
        Authentication authenticated = authenticationManager.authenticate(unauthenticated);

        // 3. Security: if the browser already had a session, give it a NEW id now.
        //    This prevents "session fixation" (an attacker planting a session id
        //    before login and reusing it after).
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        // 4. Record "this request is logged in as <user>".
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authenticated);
        securityContextHolderStrategy.setContext(context);

        // 5. IMPORTANT: save it to the HTTP session. Without this line, the login
        //    is forgotten once this request ends, and /me would return 401.
        //    This creates the SESSION cookie the browser sends from now on
        //    (CHANGED in step 8: the session itself is stored in PostgreSQL by Spring Session).
        securityContextRepository.saveContext(context, request, response);

        // authenticated.getName() is the email (see UserDetailsServiceImpl).
        return userService.getByEmail(authenticated.getName());
    }

    // GET /api/auth/me
    // Returns the currently logged-in user. React calls this on page load to know
    // whether to show the dashboard or the login page.
    // Spring injects "Authentication" for the logged-in user. Anonymous requests never
    // reach this method: SecurityConfig makes them return 401 first.
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

    // Note: there is no logout method here. POST /api/auth/logout is handled
    // by Spring Security itself (configured in SecurityConfig).
}