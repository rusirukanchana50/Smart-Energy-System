package lk.ac.ruh.eng.energy.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * CustomCsrfTokenRequestHandler
 * -----------------------------
 * Flexible CSRF token resolution supporting:
 * - Header: 'X-CSRF-TOKEN' (case-insensitive)
 * - Header: 'X-XSRF-TOKEN'
 * - Request parameter: 'csrf_token'
 * - Request parameter: '_csrf'
 */
public class CustomCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> deferredCsrfToken) {
        super.handle(request, response, deferredCsrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String token = request.getHeader("X-CSRF-TOKEN");
        if (!StringUtils.hasText(token)) {
            token = request.getHeader("X-XSRF-TOKEN");
        }
        if (!StringUtils.hasText(token)) {
            token = request.getParameter("csrf_token");
        }
        if (!StringUtils.hasText(token)) {
            token = request.getParameter("_csrf");
        }
        return StringUtils.hasText(token) ? token : super.resolveCsrfTokenValue(request, csrfToken);
    }
}
