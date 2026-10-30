package lk.ac.ruh.eng.energy.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JsonAccessDeniedHandler
 * -----------------------
 * Rejects requests with HTTP 403 JSON:
 * - If CSRF token is invalid or missing: 'CSRF validation failed: Invalid or missing CSRF token.'
 * - If user lacks required role: 'Forbidden. Administrator role required.'
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");

        if (accessDeniedException instanceof CsrfException) {
            body.put("message", "CSRF validation failed: Invalid or missing CSRF token.");
        } else {
            body.put("message", "Forbidden. Administrator role required.");
        }

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
