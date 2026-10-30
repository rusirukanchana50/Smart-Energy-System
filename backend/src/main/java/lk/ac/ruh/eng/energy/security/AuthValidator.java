package lk.ac.ruh.eng.energy.security;

import jakarta.servlet.http.HttpServletRequest;
import lk.ac.ruh.eng.energy.exception.ApiException.ForbiddenException;
import lk.ac.ruh.eng.energy.exception.ApiException.UnauthorizedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * AuthValidator
 * -------------
 * Validates authentication and role permissions strictly through Spring Security's
 * SecurityContext.
 * 
 * SECURITY ENFORCEMENT:
 * - All legacy/temporary header-based role spoofing (X-Auth-Role, X-User-Role) has been completely removed.
 * - Authenticated identity and role are strictly derived from verified Spring Security session credentials.
 */
@Component
public class AuthValidator {

    public static final String ROLE_ADMIN = "Administrator";
    public static final String ROLE_VIEWER = "Viewer";

    /**
     * Ensures the request is authenticated via Spring Security.
     * Throws HTTP 401 Unauthorized if unauthenticated.
     */
    public void requireAuth(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Unauthorized access. Please log in first.");
        }
    }

    /**
     * Ensures the request is authenticated and possesses the 'ROLE_Administrator' authority.
     * Throws HTTP 401 if unauthenticated, and HTTP 403 if user lacks Administrator privileges.
     */
    public void requireAdmin(HttpServletRequest request) {
        requireAuth(request);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equalsIgnoreCase("ROLE_Administrator") || a.equalsIgnoreCase("ROLE_ADMIN"));

        if (!isAdmin) {
            throw new ForbiddenException("Forbidden. Administrator role required.");
        }
    }

    /**
     * Resolves the verified role from the Spring Security context.
     */
    public String resolveRole(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }

        for (GrantedAuthority authority : auth.getAuthorities()) {
            String authName = authority.getAuthority();
            if (authName.startsWith("ROLE_")) {
                return authName.substring(5);
            }
            return authName;
        }

        return ROLE_VIEWER;
    }
}
