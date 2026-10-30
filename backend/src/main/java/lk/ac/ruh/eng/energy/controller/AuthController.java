package lk.ac.ruh.eng.energy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lk.ac.ruh.eng.energy.dto.LoginRequest;
import lk.ac.ruh.eng.energy.exception.ApiException.BadRequestException;
import lk.ac.ruh.eng.energy.exception.ApiException.UnauthorizedException;
import lk.ac.ruh.eng.energy.model.User;
import lk.ac.ruh.eng.energy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * AuthController
 * --------------
 * REST controller for session-based user authentication, CSRF token retrieval,
 * current user profile retrieval, and logout.
 */
@RestController
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final HttpSessionCsrfTokenRepository csrfTokenRepository;

    @Autowired
    public AuthController(AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          HttpSessionCsrfTokenRepository csrfTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    /**
     * POST /api/auth/login and POST /login
     * Authenticates user credentials and establishes server-side session.
     */
    @PostMapping(path = {"/api/auth/login", "/login"})
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody(required = false) LoginRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {

        String email = requestBody != null && requestBody.getEmail() != null ? requestBody.getEmail().trim() : request.getParameter("email");
        String password = requestBody != null && requestBody.getPassword() != null ? requestBody.getPassword() : request.getParameter("password");

        if (email == null || email.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new BadRequestException("Please provide both email and password.");
        }

        email = email.trim().toLowerCase();

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );

            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(auth);
            SecurityContextHolder.setContext(securityContext);

            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

            User user = userRepository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new UnauthorizedException("Invalid email or password. Please try again."));

            session.setAttribute("userId", user.getId());
            session.setAttribute("email", user.getEmail());
            session.setAttribute("role", user.getRole());
            session.setAttribute("full_name", user.getFullName());

            // Ensure CSRF token is initialized for the session
            CsrfToken csrfToken = csrfTokenRepository.loadToken(request);
            if (csrfToken == null) {
                csrfToken = csrfTokenRepository.generateToken(request);
                csrfTokenRepository.saveToken(csrfToken, request, response);
            }

            Map<String, Object> userData = new LinkedHashMap<>();
            userData.put("id", user.getId());
            userData.put("email", user.getEmail());
            userData.put("full_name", user.getFullName());
            userData.put("role", user.getRole());

            Map<String, Object> responseBody = new LinkedHashMap<>();
            responseBody.put("status", "success");
            responseBody.put("message", "Login successful.");
            responseBody.put("user", userData);
            responseBody.put("csrf_token", csrfToken.getToken());
            responseBody.put("redirect", "/dashboard");

            return ResponseEntity.ok(responseBody);

        } catch (AuthenticationException ex) {
            throw new UnauthorizedException("Invalid email or password. Please try again.");
        }
    }

    /**
     * GET /api/auth/me
     * Retrieves profile and role of currently authenticated session user.
     */
    @GetMapping("/api/auth/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Unauthorized access. Please log in first.");
        }

        String email = auth.getName();
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            throw new UnauthorizedException("User account no longer exists.");
        }

        User user = userOpt.get();

        CsrfToken csrfToken = csrfTokenRepository.loadToken(request);
        if (csrfToken == null) {
            csrfToken = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(csrfToken, request, response);
        }

        Map<String, Object> userData = new LinkedHashMap<>();
        userData.put("id", user.getId());
        userData.put("email", user.getEmail());
        userData.put("full_name", user.getFullName());
        userData.put("role", user.getRole());

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("status", "success");
        responseBody.put("user", userData);
        responseBody.put("csrf_token", csrfToken != null ? csrfToken.getToken() : null);

        return ResponseEntity.ok(responseBody);
    }

    /**
     * GET /api/auth/csrf
     * Retrieves or generates current CSRF token.
     */
    @GetMapping("/api/auth/csrf")
    public ResponseEntity<Map<String, Object>> getCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken csrfToken = csrfTokenRepository.loadToken(request);
        if (csrfToken == null) {
            csrfToken = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(csrfToken, request, response);
        }

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("status", "success");
        responseBody.put("csrf_token", csrfToken.getToken());

        return ResponseEntity.ok(responseBody);
    }

    /**
     * POST /api/auth/logout, POST /logout, GET /logout
     * Invalidates active session and clears security context.
     */
    @RequestMapping(path = {"/api/auth/logout", "/logout"}, method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("status", "success");
        responseBody.put("message", "Logged out successfully.");

        return ResponseEntity.ok(responseBody);
    }
}
