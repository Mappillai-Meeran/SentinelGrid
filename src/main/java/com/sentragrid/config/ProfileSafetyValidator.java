package com.sentragrid.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Fail-safe production validator enforcing explicit, safe Spring profile selection.
 * Prevents silent fallback to unvalidated default configurations or accidental
 * development assumptions in production deployments (SEC-05).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileSafetyValidator {

    private final Environment environment;

    private static final Set<String> ALLOWED_PROFILES = Set.of(
            "prod", "production", "docker", "local", "dev", "test", "staging", "demo", "cloud-demo"
    );

    @PostConstruct
    public void validateProfileConfiguration() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles == null || activeProfiles.length == 0) {
            log.error("CRITICAL CONFIGURATION ERROR: No active Spring profile specified. Application startup aborted.");
            throw new IllegalStateException(
                    "CRITICAL CONFIGURATION ERROR: No active Spring profile specified. " +
                    "SentinelGrid refuses to start with ambiguous default configuration. " +
                    "Explicitly set SPRING_PROFILES_ACTIVE to 'local' for local development, " +
                    "or 'prod' (or 'prod,docker') for production deployments."
            );
        }

        for (String profile : activeProfiles) {
            String normalized = profile.trim().toLowerCase();
            if (!ALLOWED_PROFILES.contains(normalized)) {
                log.error("CRITICAL CONFIGURATION ERROR: Unrecognized Spring profile '{}'. Allowed profiles: {}", profile, ALLOWED_PROFILES);
                throw new IllegalStateException(
                        "CRITICAL CONFIGURATION ERROR: Unrecognized Spring profile '" + profile + "'. " +
                        "Allowed profiles: " + ALLOWED_PROFILES
                );
            }
        }

        log.info("SentinelGrid profile configuration validated successfully. Active profiles: {}",
                Arrays.toString(activeProfiles));
    }
}

