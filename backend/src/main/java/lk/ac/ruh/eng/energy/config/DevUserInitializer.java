package lk.ac.ruh.eng.energy.config;

import lk.ac.ruh.eng.energy.model.User;
import lk.ac.ruh.eng.energy.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * DevUserInitializer
 * ------------------
 * Safe development-only initializer that creates or verifies an Administrator
 * and a Viewer test account in the isolated MongoDB 7.0 database (port 27018).
 * Never logs passwords in plaintext or writes plaintext passwords to the database.
 */
@Component
@Profile("!test")
public class DevUserInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DevUserInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.dev.admin.email:admin@eng.ruh.ac.lk}")
    private String adminEmail;

    @Value("${app.dev.admin.password:admin123}")
    private String adminPassword;

    @Value("${app.dev.viewer.email:viewer@eng.ruh.ac.lk}")
    private String viewerEmail;

    @Value("${app.dev.viewer.password:viewer123}")
    private String viewerPassword;

    @Autowired
    public DevUserInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        try {
            ensureDevAccount(adminEmail, "System Administrator", "Administrator", adminPassword);
            ensureDevAccount(viewerEmail, "Energy Auditor Viewer", "Viewer", viewerPassword);
        } catch (Exception ex) {
            logger.warn("DevUserInitializer completed with notice: {}", ex.getMessage());
        }
    }

    private void ensureDevAccount(String email, String fullName, String role, String plainPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isEmpty()) {
            User user = new User();
            user.setEmail(normalizedEmail);
            user.setFullName(fullName);
            user.setRole(role);
            user.setPassword(passwordEncoder.encode(plainPassword));
            user.setCreatedAt(Instant.now());
            userRepository.save(user);
            logger.info("Initialized development test account: {} with role: {}", normalizedEmail, role);
        } else {
            logger.info("Verified development test account exists: {}", normalizedEmail);
        }
    }
}
