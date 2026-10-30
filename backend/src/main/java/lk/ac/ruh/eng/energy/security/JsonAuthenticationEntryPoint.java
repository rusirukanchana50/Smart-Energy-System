package lk.ac.ruh.eng.energy.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JsonAuthenticationEntryPoint
 * ----------------------------
 * Rejects unauthenticated requests to protected endpoints with HTTP 401 JSON.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");

        // If not an API request and client accepts HTML, redirect browser page navigation to /login
        if (!uri.startsWith("/api/") && (accept != null && (accept.contains("text/html") || accept.contains("*/*")))) {
            response.sendRedirect("/login");
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("message", "Unauthorized access. Please log in first.");

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
