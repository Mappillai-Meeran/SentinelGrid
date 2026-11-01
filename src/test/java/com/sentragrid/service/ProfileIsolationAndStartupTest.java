package com.sentragrid.service;

import com.sentragrid.config.DemoDataInitializer;
import com.sentragrid.repository.PharmacyRepository;
import com.sentragrid.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@DisplayName("Step 5B.1 - Profile Isolation and Startup Safety Tests")
class ProfileIsolationAndStartupTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(PharmacyRepository.class, () -> mock(PharmacyRepository.class))
            .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withUserConfiguration(DemoDataInitializer.class);

    private final YamlPropertySourceLoader yamlLoader = new YamlPropertySourceLoader();

    @Test
    @DisplayName("Local profile activates DemoDataInitializer")
    void testLocalProfileActivatesDemoInitializer() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local")
                .run(context -> {
                    assertThat(context).hasSingleBean(DemoDataInitializer.class);
                });
    }

    @Test
    @DisplayName("Dev profile strictly disables DemoDataInitializer")
    void testDevProfileExcludesDemoInitializer() {
        contextRunner
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DemoDataInitializer.class);
                });
    }

    @Test
    @DisplayName("Production profile strictly disables DemoDataInitializer")
    void testProdProfileExcludesDemoInitializer() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DemoDataInitializer.class);
                });
    }

    @Test
    @DisplayName("Docker profile strictly disables DemoDataInitializer")
    void testDockerProfileExcludesDemoInitializer() {
        contextRunner
                .withPropertyValues("spring.profiles.active=docker")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DemoDataInitializer.class);
                });
    }

    @Test
    @DisplayName("Default startup with no profile strictly disables DemoDataInitializer")
    void testDefaultNoProfileExcludesDemoInitializer() {
        contextRunner
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DemoDataInitializer.class);
                });
    }

    @Test
    @DisplayName("Base application.yml configures spring.sql.init.mode=never as safe default")
    void testDefaultProfileSqlInitModeIsNever() throws IOException {
        List<PropertySource<?>> sources = yamlLoader.load("application.yml", new ClassPathResource("application.yml"));
        assertThat(sources).isNotEmpty();
        assertEquals("never", sources.get(0).getProperty("spring.sql.init.mode"),
                "Base application.yml must disable SQL initialization by default");
    }

    @Test
    @DisplayName("Production profile application-prod.yml configures spring.sql.init.mode=never")
    void testProdProfileSqlInitModeIsNever() throws IOException {
        List<PropertySource<?>> sources = yamlLoader.load("application-prod.yml", new ClassPathResource("application-prod.yml"));
        assertThat(sources).isNotEmpty();
        assertEquals("never", sources.get(0).getProperty("spring.sql.init.mode"),
                "Production profile must explicitly disable SQL initialization");
    }

    @Test
    @DisplayName("Docker profile application-docker.yml configures spring.sql.init.mode=never")
    void testDockerProfileSqlInitModeIsNever() throws IOException {
        List<PropertySource<?>> sources = yamlLoader.load("application-docker.yml", new ClassPathResource("application-docker.yml"));
        assertThat(sources).isNotEmpty();
        assertEquals("never", sources.get(0).getProperty("spring.sql.init.mode"),
                "Docker profile must explicitly disable SQL initialization by default");
    }

    @Test
    @DisplayName("Local profile application-local.yml configures spring.sql.init.mode=always for intentional seeding")
    void testLocalProfileSqlInitModeIsAlways() throws IOException {
        List<PropertySource<?>> sources = yamlLoader.load("application-local.yml", new ClassPathResource("application-local.yml"));
        assertThat(sources).isNotEmpty();
        assertEquals("always", sources.get(0).getProperty("spring.sql.init.mode"),
                "Local profile must enable SQL initialization for development seeding");
    }

    @Test
    @DisplayName("Docker profile application-docker.yml requires explicit JWT_SECRET without development placeholder fallback")
    void testDockerProfileRequiresExplicitJwtSecret() throws IOException {
        List<PropertySource<?>> sources = yamlLoader.load("application-docker.yml", new ClassPathResource("application-docker.yml"));
        assertThat(sources).isNotEmpty();
        String secretConfig = (String) sources.get(0).getProperty("app.jwt.secret");
        assertThat(secretConfig).isEqualTo("${JWT_SECRET}");
        assertThat(secretConfig).doesNotContain("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
    }

    @Test
    @DisplayName("Base docker-compose.yml mandates explicit JWT_SECRET and rejects fallback placeholder")
    void testDockerComposeMandatesExplicitJwtSecret() throws IOException {
        java.nio.file.Path composePath = java.nio.file.Path.of("docker-compose.yml");
        assertThat(composePath).exists();
        String content = java.nio.file.Files.readString(composePath);
        assertThat(content).contains("JWT_SECRET=${JWT_SECRET:?CRITICAL: JWT_SECRET must be explicitly set for Docker deployment");
        assertThat(content).doesNotContain("JWT_SECRET=${JWT_SECRET:-404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}");
    }

    @Test
    @DisplayName("SEC-05: ProfileSafetyValidator fails fast when no active profile is specified")
    void testProfileSafetyValidatorFailsOnEmptyProfile() {
        org.springframework.mock.env.MockEnvironment mockEnv = new org.springframework.mock.env.MockEnvironment();
        com.sentragrid.config.ProfileSafetyValidator validator = new com.sentragrid.config.ProfileSafetyValidator(mockEnv);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, validator::validateProfileConfiguration);
    }

    @Test
    @DisplayName("SEC-05: ProfileSafetyValidator fails fast when unknown profile is specified")
    void testProfileSafetyValidatorFailsOnUnknownProfile() {
        org.springframework.mock.env.MockEnvironment mockEnv = new org.springframework.mock.env.MockEnvironment();
        mockEnv.setActiveProfiles("malicious_unrecognized_profile");
        com.sentragrid.config.ProfileSafetyValidator validator = new com.sentragrid.config.ProfileSafetyValidator(mockEnv);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, validator::validateProfileConfiguration);
    }

    @Test
    @DisplayName("SEC-05: ProfileSafetyValidator accepts legitimate production and dev profiles")
    void testProfileSafetyValidatorAcceptsLegitimateProfiles() {
        for (String validProfile : List.of("prod", "docker", "local", "dev", "test", "staging")) {
            org.springframework.mock.env.MockEnvironment mockEnv = new org.springframework.mock.env.MockEnvironment();
            mockEnv.setActiveProfiles(validProfile);
            com.sentragrid.config.ProfileSafetyValidator validator = new com.sentragrid.config.ProfileSafetyValidator(mockEnv);
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(validator::validateProfileConfiguration);
        }
    }

    @Test
    @DisplayName("SEC-06: Base application.yml configures ddl-auto=validate as secure default")
    void testBaseApplicationConfiguresDdlAutoValidate() throws IOException {
        List<PropertySource<?>> sources = yamlLoader.load("application.yml", new ClassPathResource("application.yml"));
        assertThat(sources).isNotEmpty();
        assertEquals("validate", sources.get(0).getProperty("spring.jpa.hibernate.ddl-auto"),
                "Base application.yml must enforce ddl-auto: validate");
    }

    @Test
    @DisplayName("SEC-06: Production application-prod.yml configures ddl-auto=validate")
    void testProdApplicationConfiguresDdlAutoValidate() throws IOException {
        List<PropertySource<?>> sources = yamlLoader.load("application-prod.yml", new ClassPathResource("application-prod.yml"));
        assertThat(sources).isNotEmpty();
        assertEquals("validate", sources.get(0).getProperty("spring.jpa.hibernate.ddl-auto"),
                "Production profile must enforce ddl-auto: validate");
    }

    @Test
    @DisplayName("SEC-06: Docker application-docker.yml configures ddl-auto validate with override support")
    void testDockerApplicationConfiguresDdlAutoValidate() throws IOException {
        List<PropertySource<?>> sources = yamlLoader.load("application-docker.yml", new ClassPathResource("application-docker.yml"));
        assertThat(sources).isNotEmpty();
        assertEquals("${SPRING_JPA_HIBERNATE_DDL_AUTO:validate}", sources.get(0).getProperty("spring.jpa.hibernate.ddl-auto"),
                "Docker profile must default to ddl-auto: validate");
    }

    @Test
    @DisplayName("SEC-06: Local application-local.yml configures ddl-auto=update for dev convenience")
    void testLocalApplicationConfiguresDdlAutoUpdate() throws IOException {
        List<PropertySource<?>> sources = yamlLoader.load("application-local.yml", new ClassPathResource("application-local.yml"));
        assertThat(sources).isNotEmpty();
        assertEquals("update", sources.get(0).getProperty("spring.jpa.hibernate.ddl-auto"),
                "Local profile must configure ddl-auto: update");
    }
}
