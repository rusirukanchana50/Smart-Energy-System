package lk.ac.ruh.eng.energy.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * SecurityConfig
 * --------------
 * Configures Spring Security session-based authentication, role-based access control,
 * and CSRF protection.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint;
    private final JsonAccessDeniedHandler jsonAccessDeniedHandler;

    @Autowired
    public SecurityConfig(JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint,
                          JsonAccessDeniedHandler jsonAccessDeniedHandler) {
        this.jsonAuthenticationEntryPoint = jsonAuthenticationEntryPoint;
        this.jsonAccessDeniedHandler = jsonAccessDeniedHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public HttpSessionCsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        repository.setParameterName("_csrf");
        return repository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CustomCsrfTokenRequestHandler csrfTokenRequestHandler = new CustomCsrfTokenRequestHandler();

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository())
                .csrfTokenRequestHandler(csrfTokenRequestHandler)
                .ignoringRequestMatchers(
                    new AntPathRequestMatcher("/api/health"),
                    new AntPathRequestMatcher("/api/auth/login"),
                    new AntPathRequestMatcher("/login")
                )
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jsonAuthenticationEntryPoint)
                .accessDeniedHandler(jsonAccessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                // Secure dashboard, alerts, analytics, cost-efficiency, reports, and printable report endpoints to prevent static resource bypass
                .requestMatchers("/dashboard", "/dashboard.html", "/static/dashboard.html",
                                 "/alerts", "/alerts.html", "/static/alerts.html",
                                 "/analytics", "/analytics.html", "/static/analytics.html",
                                 "/cost-efficiency", "/cost-efficiency.html", "/cost_efficiency.html",
                                 "/static/cost_efficiency.html", "/static/cost-efficiency.html",
                                 "/reports", "/reports.html", "/static/reports.html",
                                 "/reports/printable", "/reports/printable.html",
                                 "/printable_report.html", "/static/printable_report.html").authenticated()

                // Public endpoints: landing pages, login endpoints, static resources
                .requestMatchers(
                    "/",
                    "/login",
                    "/login.html",
                    "/api/health",
                    "/api/auth/login",
                    "/api/auth/csrf",
                    "/static/**",
                    "/css/**",
                    "/js/**",
                    "/favicon.ico"
                ).permitAll()

                // Administrator-only Building mutations and deletion preview
                .requestMatchers(HttpMethod.POST, "/api/buildings", "/api/buildings/**").hasRole("Administrator")
                .requestMatchers(HttpMethod.PUT, "/api/buildings/**").hasRole("Administrator")
                .requestMatchers(HttpMethod.DELETE, "/api/buildings/**").hasRole("Administrator")
                .requestMatchers(HttpMethod.GET, "/api/buildings/*/delete-preview").hasRole("Administrator")

                // Administrator-only electrical alert acknowledgment
                .requestMatchers(HttpMethod.POST, "/api/alerts/**").hasRole("Administrator")

                // Administrator-only configuration updates (Tariff, Faculty Settings)
                .requestMatchers(HttpMethod.POST, "/api/config", "/api/config/**").hasRole("Administrator")
                .requestMatchers(HttpMethod.PUT, "/api/config", "/api/config/**").hasRole("Administrator")

                // Authenticated read access (Administrator & Viewer)
                .requestMatchers(HttpMethod.GET, "/api/buildings", "/api/buildings/**", "/api/energy", "/api/energy/**", "/api/alerts", "/api/alerts/**", "/api/config", "/api/config/**", "/api/reports", "/api/reports/**").hasAnyRole("Administrator", "Viewer")
                .requestMatchers("/dashboard", "/analytics", "/alerts", "/cost-efficiency", "/reports/**").authenticated()
                .requestMatchers("/api/auth/me", "/api/auth/logout", "/logout").authenticated()

                // Any other request must be authenticated
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
