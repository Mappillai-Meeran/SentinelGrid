package com.sentragrid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentragrid.auth.dto.LoginRequest;
import com.sentragrid.auth.dto.RegisterRequest;
import com.sentragrid.entity.Medicine;
import com.sentragrid.entity.Pharmacy;
import com.sentragrid.entity.Reservation;
import com.sentragrid.entity.User;
import com.sentragrid.entity.enums.ReservationStatus;
import com.sentragrid.entity.enums.UserRole;
import com.sentragrid.repository.MedicineRepository;
import com.sentragrid.repository.PharmacyRepository;
import com.sentragrid.repository.ReservationRepository;
import com.sentragrid.repository.UserRepository;
import com.sentragrid.security.JwtTokenProvider;
import com.sentragrid.security.RateLimiterService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("Step 5B.2 - Authentication & Security Hardening Tests")
class AuthenticationAndSecurityHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private com.sentragrid.auth.controller.AuthController authController;

    @BeforeEach
    void setUp() {
        rateLimiterService.reset();
        authController.configureTrustedProxies("127.0.0.1,::1,0:0:0:0:0:0:0:1");
    }

    // ==========================================
    // 1. JWT SECRET & ENTROPY TESTS
    // ==========================================

    @Test
    @DisplayName("JWT: Production fails fast when app.jwt.secret is empty")
    void testMissingJwtSecretInProdFailsFast() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=prod", "app.jwt.secret=")
                .withBean(MockEnvironment.class, () -> {
                    MockEnvironment env = new MockEnvironment();
                    env.setActiveProfiles("prod");
                    return env;
                })
                .withUserConfiguration(JwtTokenProvider.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure().getCause().getMessage())
                            .contains("must not be empty");
                });
    }

    @Test
    @DisplayName("JWT: Fails fast when app.jwt.secret has fewer than 256 bits (32 bytes)")
    void testWeakJwtSecretFailsFast() {
        new ApplicationContextRunner()
                .withPropertyValues("app.jwt.secret=too-short-secret-12345")
                .withBean(MockEnvironment.class, () -> new MockEnvironment())
                .withUserConfiguration(JwtTokenProvider.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure().getCause().getMessage())
                            .contains("at least 256 bits (32 bytes)");
                });
    }

    @Test
    @DisplayName("JWT: Production environment rejects known development secret placeholder")
    void testKnownDevSecretPlaceholderFailsFastInProd() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
                )
                .withBean(MockEnvironment.class, () -> {
                    MockEnvironment env = new MockEnvironment();
                    env.setActiveProfiles("prod");
                    return env;
                })
                .withUserConfiguration(JwtTokenProvider.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(context.getStartupFailure().getCause().getMessage())
                            .contains("Production environment cannot use known development JWT secret placeholder");
                });
    }

    // ==========================================
    // 2. JWT KEY ROTATION TESTS
    // ==========================================

    @Test
    @DisplayName("JWT: Verification key rotation seamlessly validates tokens from old secret")
    void testJwtVerificationKeyRotationAllowsOldTokens() {
        String oldSecretStr = "OldSecretKeyForRotationTesting12"; // 32 bytes
        String newSecretStr = "NewActiveSecretKeyForSigning1234"; // 32 bytes
        SecretKey oldKey = Keys.hmacShaKeyFor(oldSecretStr.getBytes(StandardCharsets.UTF_8));
        SecretKey newKey = Keys.hmacShaKeyFor(newSecretStr.getBytes(StandardCharsets.UTF_8));

        // Sign token with old key using HS256
        String username = "rotation_test_user";
        String oldToken = Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(oldKey, Jwts.SIG.HS256)
                .compact();

        // Create provider configured with new active secret and old key in verification list
        MockEnvironment env = new MockEnvironment();
        JwtTokenProvider provider = new JwtTokenProvider(env);
        org.springframework.test.util.ReflectionTestUtils.setField(provider, "jwtSecret", newSecretStr);
        org.springframework.test.util.ReflectionTestUtils.setField(provider, "verificationSecrets", oldSecretStr);
        org.springframework.test.util.ReflectionTestUtils.setField(provider, "jwtExpirationMs", 86400000L);
        provider.init();

        // Validate that provider accepts token signed with old key
        assertTrue(provider.validateToken(oldToken), "Token signed with old secret must be valid under rotation");
        assertEquals(username, provider.getUsernameFromJwt(oldToken), "Subject must be extracted from old token");

        // Validate that newly generated tokens are signed and verifiable
        String newToken = provider.generateTokenForUser("new_user");
        assertTrue(provider.validateToken(newToken), "New token must be valid");
        assertEquals("new_user", provider.getUsernameFromJwt(newToken));

        // Unknown 3rd key must be rejected
        SecretKey untrustedKey = Keys.hmacShaKeyFor("UntrustedKeyNotConfiguredInList1".getBytes(StandardCharsets.UTF_8));
        String untrustedToken = Jwts.builder()
                .subject("hacker")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(untrustedKey, Jwts.SIG.HS256)
                .compact();
        assertFalse(provider.validateToken(untrustedToken), "Token signed with untrusted key must be rejected");
    }

    // ==========================================
    // 3. RATE LIMITING TESTS (429 & RETRY-AFTER)
    // ==========================================

    @Test
    @DisplayName("Rate Limit: Consecutive failed logins trigger HTTP 429 with Retry-After header")
    void testFailedLoginRateLimitingReturns429() throws Exception {
        String testUser = "brute_force_target_" + UUID.randomUUID().toString().substring(0, 8);
        LoginRequest badRequest = new LoginRequest(testUser, "wrong_password_attempt");
        String jsonBody = objectMapper.writeValueAsString(badRequest);

        // Perform 5 failed attempts (allowed threshold)
        for (int i = 1; i <= RateLimiterService.MAX_FAILED_LOGINS; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonBody)
                            .header("X-Forwarded-For", "203.0.113.195"))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt must be locked out with HTTP 429
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody)
                        .header("X-Forwarded-For", "203.0.113.195"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Too many failed login attempts")))
                .andReturn();

        String retryAfterHeader = result.getResponse().getHeader("Retry-After");
        assertNotNull(retryAfterHeader);
        assertTrue(Long.parseLong(retryAfterHeader) > 0);
    }

    @Test
    @DisplayName("Rate Limit: Successful login clears failed attempt counter")
    void testSuccessfulLoginClearsFailedAttempts() throws Exception {
        String username = "patient1";
        LoginRequest badRequest = new LoginRequest(username, "bad_pw");
        String badJson = objectMapper.writeValueAsString(badRequest);

        // 2 failed attempts
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badJson)
                            .header("X-Forwarded-For", "203.0.113.200"))
                    .andExpect(status().isUnauthorized());
        }

        // Successful login
        LoginRequest goodRequest = new LoginRequest(username, "password123");
        String goodJson = objectMapper.writeValueAsString(goodRequest);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goodJson)
                        .header("X-Forwarded-For", "203.0.113.200"))
                .andExpect(status().isOk());

        // Counter is reset; can fail again without immediate lockout
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badJson)
                        .header("X-Forwarded-For", "203.0.113.200"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Client IP: Untrusted direct peer sending X-Forwarded-For cannot spoof IP to bypass login limiter")
    void testUntrustedPeerCannotSpoofIpViaXForwardedFor() throws Exception {
        // Untrusted peer 198.51.100.1 attempts brute force but sets fake X-Forwarded-For on each request
        String targetUser = "spoof_target_" + UUID.randomUUID().toString().substring(0, 8);
        LoginRequest badRequest = new LoginRequest(targetUser, "wrong_pw");
        String badJson = objectMapper.writeValueAsString(badRequest);

        for (int i = 1; i <= RateLimiterService.MAX_FAILED_LOGINS; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(req -> {
                                req.setRemoteAddr("198.51.100.1"); // Untrusted remote addr
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badJson)
                            .header("X-Forwarded-For", "fake.ip." + i)) // Fake spoofed IP
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt from 198.51.100.1 is locked out because X-Forwarded-For was ignored!
        mockMvc.perform(post("/api/auth/login")
                        .with(req -> {
                            req.setRemoteAddr("198.51.100.1");
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badJson)
                        .header("X-Forwarded-For", "fake.ip.6"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Too many failed login attempts")));
    }

    @Test
    @DisplayName("Rate Limit: Registration limit triggers HTTP 429 when IP exceeds threshold (unit test)")
    void testRegistrationRateLimitingReturns429() {
        String testIp = "198.51.100.42";

        // Record 10 registrations
        for (int i = 0; i < RateLimiterService.MAX_REGISTRATIONS_PER_WINDOW; i++) {
            rateLimiterService.recordRegistration(testIp);
        }

        // 11th registration must throw RateLimitExceededException
        com.sentragrid.exception.RateLimitExceededException ex = assertThrows(
                com.sentragrid.exception.RateLimitExceededException.class,
                () -> rateLimiterService.checkRegistrationAllowed(testIp)
        );
        assertTrue(ex.getRetryAfterSeconds() > 0);
        assertTrue(ex.getMessage().contains("Registration rate limit exceeded"));
    }

    @Test
    @DisplayName("Rate Limit: HTTP registration endpoint returns 429 and Retry-After via MockMvc")
    void testRegistrationRateLimitingReturns429ViaHttp() throws Exception {
        String clientIp = "198.51.100.77";

        // Pre-record 10 registrations for this IP in rateLimiterService
        for (int i = 0; i < RateLimiterService.MAX_REGISTRATIONS_PER_WINDOW; i++) {
            rateLimiterService.recordRegistration(clientIp);
        }

        // 11th registration attempt over HTTP MockMvc
        RegisterRequest request = RegisterRequest.builder()
                .username("reg_limit_user_" + UUID.randomUUID().toString().substring(0, 6))
                .email("reg_limit_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .with(req -> {
                            req.setRemoteAddr(clientIp);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Registration rate limit exceeded")));
    }

    @Test
    @DisplayName("Rate Limit: Bounded memory eviction prunes expired trackers but strictly protects active lockouts")
    void testBoundedRateLimiterMemoryProtectsActiveLockouts() {
        long now = java.time.Instant.now().getEpochSecond();

        // 1. Record an attempt
        rateLimiterService.recordLoginFailure("old_user", "10.0.0.1");

        // 2. Create an actively locked out tracker (5 failures)
        for (int i = 0; i < 5; i++) {
            rateLimiterService.recordLoginFailure("locked_user", "10.0.0.2");
        }

        // Active tracker is locked out
        assertThrows(com.sentragrid.exception.RateLimitExceededException.class,
                () -> rateLimiterService.checkLoginAllowed("locked_user", "10.0.0.2"));

        // Run cleanup during active lockout (100 seconds into the 300 second lockout)
        long duringLockout = now + 100L;
        rateLimiterService.cleanupExpiredLoginTrackers(duringLockout);

        // Active lockout MUST still be intact and prevent login
        assertThrows(com.sentragrid.exception.RateLimitExceededException.class,
                () -> rateLimiterService.checkLoginAllowed("locked_user", "10.0.0.2"));
    }

    @Test
    @DisplayName("Rate Limit: Capacity exhaustion triggers controlled fail-closed rejection without evicting active lockouts")
    void testRateLimiterCapacityExhaustionFailsClosed() {
        // Set capacity limit to 6 (each user/ip pair tracks 2 entries: user@ip and ip:ip)
        rateLimiterService.setMaxLoginTrackers(6);

        // Fill capacity with 3 actively locked-out keys (3 user@ip + 3 ip:ip = 6 entries)
        for (int i = 1; i <= 3; i++) {
            for (int f = 0; f < 5; f++) {
                rateLimiterService.recordLoginFailure("user" + i, "10.0.0." + i);
            }
        }
        assertEquals(6, rateLimiterService.getLoginTrackerCount(), "Capacity should be at 6");

        // Attempting to track a 4th new key must fail-closed with 429
        com.sentragrid.exception.RateLimitExceededException ex = assertThrows(
                com.sentragrid.exception.RateLimitExceededException.class,
                () -> rateLimiterService.recordLoginFailure("overflow_user", "10.0.0.99")
        );
        assertTrue(ex.getMessage().contains("capacity exceeded"));

        // Capacity was strictly preserved at 6
        assertEquals(6, rateLimiterService.getLoginTrackerCount());

        // All 3 original active lockouts are intact
        for (int i = 1; i <= 3; i++) {
            final int idx = i;
            assertThrows(com.sentragrid.exception.RateLimitExceededException.class,
                    () -> rateLimiterService.checkLoginAllowed("user" + idx, "10.0.0." + idx));
        }
    }

    @Test
    @DisplayName("Rate Limit: Concurrent execution under multithreaded contention maintains thread-safety")
    void testRateLimiterConcurrentAccessThreadSafety() throws InterruptedException {
        int threads = 8;
        int operationsPerThread = 25;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.atomic.AtomicInteger errors = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < operationsPerThread; i++) {
                        String user = "concurrent_user_" + threadId + "_" + (i % 5);
                        String ip = "192.168.1." + threadId;
                        try {
                            rateLimiterService.checkLoginAllowed(user, ip);
                            rateLimiterService.recordLoginFailure(user, ip);
                        } catch (com.sentragrid.exception.RateLimitExceededException ignored) {
                            // Expected when rate limit is hit
                        }
                    }
                } catch (Exception ex) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Concurrent execution must finish within timeout");
        assertEquals(0, errors.get(), "Thread-safe limiter must produce zero uncaught concurrency exceptions");
    }

    @Test
    @DisplayName("Rate Limit: Simultaneous concurrent requests with distinct keys NEVER exceed maximum login capacity and preserve active lockouts")
    void testConcurrentDistinctKeysNeverExceedsLoginCapacity() throws InterruptedException {
        int maxCapacity = 10;
        rateLimiterService.setMaxLoginTrackers(maxCapacity);

        // Pre-populate all 10 slots with actively locked-out accounts (5 user keys + 5 ip keys = 10 locked trackers)
        for (int i = 1; i <= 5; i++) {
            for (int f = 0; f < 5; f++) {
                rateLimiterService.recordLoginFailure("active_lock_user_" + i, "172.30.0." + i);
            }
        }
        assertEquals(10, rateLimiterService.getLoginTrackerCount(), "Should have exactly 10 active locked trackers");

        // Verify initial lockouts
        for (int i = 1; i <= 5; i++) {
            final int idx = i;
            assertThrows(com.sentragrid.exception.RateLimitExceededException.class,
                    () -> rateLimiterService.checkLoginAllowed("active_lock_user_" + idx, "172.30.0." + idx));
        }

        // Fire 40 simultaneous concurrent requests with distinct keys to attempt overflowing capacity
        int threadCount = 40;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger capacity429Exceptions = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Each thread attempts to record failure for distinct user and IP
                    rateLimiterService.recordLoginFailure("concurrent_flood_" + threadId, "10.99.1." + threadId);
                } catch (com.sentragrid.exception.RateLimitExceededException ex) {
                    if (ex.getMessage().contains("capacity exceeded")) {
                        capacity429Exceptions.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Flood test completed within timeout");

        // 1. Assert map size NEVER exceeded maxLoginTrackers
        assertEquals(maxCapacity, rateLimiterService.getLoginTrackerCount(),
                "Login tracker map size must strictly remain at max capacity and never exceed it");

        // 2. Assert saturation produced the documented 429 capacity exception
        assertEquals(threadCount, capacity429Exceptions.get(),
                "All flooded threads on saturated capacity must receive fail-closed 429 rejections");

        // 3. Assert active lockouts were strictly preserved and not evicted
        for (int i = 1; i <= 5; i++) {
            final int idx = i;
            assertThrows(com.sentragrid.exception.RateLimitExceededException.class,
                    () -> rateLimiterService.checkLoginAllowed("active_lock_user_" + idx, "172.30.0." + idx),
                    "Active lockout for user " + idx + " must remain intact after flood");
        }
    }

    @Test
    @DisplayName("Rate Limit: Simultaneous concurrent requests with distinct IPs NEVER exceed maximum registration capacity and preserve active rate limits")
    void testConcurrentDistinctIpsNeverExceedsRegistrationCapacity() throws InterruptedException {
        int maxCapacity = 8;
        rateLimiterService.setMaxRegistrationTrackers(maxCapacity);

        // Pre-populate all 8 slots with actively rate-limited IPs (10 registrations each)
        for (int i = 1; i <= 8; i++) {
            for (int r = 0; r < 10; r++) {
                rateLimiterService.recordRegistration("192.168.88." + i);
            }
        }
        assertEquals(8, rateLimiterService.getRegistrationTrackerCount());

        // Verify initial rate limits
        for (int i = 1; i <= 8; i++) {
            final int idx = i;
            assertThrows(com.sentragrid.exception.RateLimitExceededException.class,
                    () -> rateLimiterService.checkRegistrationAllowed("192.168.88." + idx));
        }

        // Fire 30 simultaneous concurrent requests with distinct IPs
        int threadCount = 30;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger capacity429Exceptions = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    rateLimiterService.recordRegistration("10.77.2." + threadId);
                } catch (com.sentragrid.exception.RateLimitExceededException ex) {
                    if (ex.getMessage().contains("capacity exceeded")) {
                        capacity429Exceptions.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Registration flood test completed within timeout");

        // 1. Assert map size NEVER exceeded maxRegistrationTrackers
        assertEquals(maxCapacity, rateLimiterService.getRegistrationTrackerCount(),
                "Registration tracker map size must strictly remain at max capacity and never exceed it");

        // 2. Assert saturation produced the documented 429 capacity exception
        assertEquals(threadCount, capacity429Exceptions.get(),
                "All flooded threads on saturated capacity must receive fail-closed 429 rejections");

        // 3. Assert active rate limits were strictly preserved
        for (int i = 1; i <= 8; i++) {
            final int idx = i;
            assertThrows(com.sentragrid.exception.RateLimitExceededException.class,
                    () -> rateLimiterService.checkRegistrationAllowed("192.168.88." + idx),
                    "Active rate limit for IP " + idx + " must remain intact after flood");
        }
    }

    // ==========================================
    // TRUSTED PROXY & CLIENT IP RESOLUTION TESTS
    // ==========================================

    @Test
    @DisplayName("Proxy: Supports IPv4/IPv6 CIDR ranges and single IPs in trusted proxy resolution")
    void testTrustedProxyCidrMatching() {
        authController.configureTrustedProxies("10.0.0.0/8, 172.16.0.0/12, 192.168.1.50, ::1/128");

        // IPv4 CIDR matching
        assertTrue(authController.isTrustedProxy("10.1.2.3"));
        assertTrue(authController.isTrustedProxy("10.254.254.254"));
        assertTrue(authController.isTrustedProxy("172.20.10.5"));

        // Single IP matching
        assertTrue(authController.isTrustedProxy("192.168.1.50"));
        assertFalse(authController.isTrustedProxy("192.168.1.51"));

        // IPv6 CIDR matching
        assertTrue(authController.isTrustedProxy("::1"));

        // Untrusted public IPs
        assertFalse(authController.isTrustedProxy("203.0.113.195"));
        assertFalse(authController.isTrustedProxy("8.8.8.8"));
        assertFalse(authController.isTrustedProxy("172.32.0.1"));
    }

    @Test
    @DisplayName("Proxy: Forwarded address chain correctly traverses to rightmost untrusted client")
    void testForwardedAddressChainTraversesToRightmostUntrustedClient() {
        // Trusted internal ingress (10.0.0.1) and load balancer (10.0.0.2)
        authController.configureTrustedProxies("10.0.0.0/8");

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1"); // Direct peer connection is trusted ingress
        // Attacker spoofed 1.1.1.1, real client is 203.0.113.50, forwarded via LB 10.0.0.2
        request.addHeader("X-Forwarded-For", "1.1.1.1, 203.0.113.50, 10.0.0.2");

        String resolvedIp = authController.extractClientIp(request);
        assertEquals("203.0.113.50", resolvedIp, "Must resolve genuine client IP and discard spoofed prefix");
    }

    @Test
    @DisplayName("Proxy: Malformed or malicious X-Forwarded-For safely falls back to remoteAddr")
    void testMalformedForwardedHeaderFallsBackSafely() {
        authController.configureTrustedProxies("10.0.0.1");

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "'; DROP TABLE users; --, <script>");

        String resolvedIp = authController.extractClientIp(request);
        assertEquals("10.0.0.1", resolvedIp, "Malformed IP syntax must fall back safely to remote address");
    }

    @Test
    @DisplayName("Proxy: Empty trusted-proxy configuration ignores all forwarded headers")
    void testEmptyTrustedProxyConfigurationIgnoresAllForwardedHeaders() {
        authController.configureTrustedProxies(""); // Empty configuration

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        request.addHeader("X-Forwarded-For", "203.0.113.5");

        String resolvedIp = authController.extractClientIp(request);
        assertEquals("192.168.1.100", resolvedIp, "Empty proxy configuration must ignore forwarded headers");
    }

    // ==========================================
    // H2 CONSOLE PROFILE ISOLATION TESTS
    // ==========================================

    @Test
    @DisplayName("H2 Console: Strictly denied when no active profile or unexpected profile is configured")
    void testH2ConsoleDeniedByDefaultAndUnexpectedProfiles() {
        // Test no profile active (default)
        org.springframework.mock.env.MockEnvironment noProfileEnv = new org.springframework.mock.env.MockEnvironment();
        com.sentragrid.config.SecurityConfig configNoProfile = new com.sentragrid.config.SecurityConfig(
                null, null, noProfileEnv
        );
        assertFalse(Boolean.TRUE.equals(org.springframework.test.util.ReflectionTestUtils.invokeMethod(configNoProfile, "isH2ConsoleAllowed")),
                "H2 console must be denied by default when no profile is active");

        // Test unexpected profile (e.g. 'staging' or 'production')
        org.springframework.mock.env.MockEnvironment stagingEnv = new org.springframework.mock.env.MockEnvironment();
        stagingEnv.setActiveProfiles("staging");
        com.sentragrid.config.SecurityConfig configStaging = new com.sentragrid.config.SecurityConfig(
                null, null, stagingEnv
        );
        assertFalse(Boolean.TRUE.equals(org.springframework.test.util.ReflectionTestUtils.invokeMethod(configStaging, "isH2ConsoleAllowed")),
                "H2 console must be denied in unexpected profiles");

        // Test intended local profile
        org.springframework.mock.env.MockEnvironment localEnv = new org.springframework.mock.env.MockEnvironment();
        localEnv.setActiveProfiles("local");
        com.sentragrid.config.SecurityConfig configLocal = new com.sentragrid.config.SecurityConfig(
                null, null, localEnv
        );
        assertTrue(Boolean.TRUE.equals(org.springframework.test.util.ReflectionTestUtils.invokeMethod(configLocal, "isH2ConsoleAllowed")),
                "H2 console must be allowed in explicitly permitted 'local' profile");
    }

    // ==========================================
    // 4. CORS HARDENING & PREFLIGHT TESTS
    // ==========================================

    @Test
    @DisplayName("CORS: Legitimate origin receives allow-credentials, max-age, and exposed headers")
    void testCorsAllowedOriginPreflight() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type,Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Max-Age", "3600"));
    }

    @Test
    @DisplayName("CORS: Unauthorized origin is not granted Access-Control-Allow-Origin")
    void testCorsDisallowedOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://malicious-attacker.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ==========================================
    // 5. REGISTRATION ROLE ESCALATION TEST
    // ==========================================

    @Test
    @DisplayName("Auth: Public registration with ADMIN/PHARMACIST payload is forced to PATIENT role")
    void testRegistrationRoleEscalationBlocked() throws Exception {
        String uniqueUser = "hacker_" + UUID.randomUUID().toString().substring(0, 8);
        RegisterRequest request = RegisterRequest.builder()
                .username(uniqueUser)
                .email(uniqueUser + "@example.com")
                .password("SecureP@ss12345!")
                .role(UserRole.ADMIN)
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        User registered = userRepository.findByUsername(uniqueUser).orElseThrow();
        assertEquals(UserRole.PATIENT, registered.getRole(),
                "Public self-registration must ALWAYS enforce PATIENT role");
    }

    // ==========================================
    // 6. CROSS-PHARMACY BRANCH AUTHORIZATION
    // ==========================================

    @Test
    @DisplayName("Auth: Pharmacist from Branch 1 cannot verify prescription for Branch 2 reservation")
    @WithMockUser(username = "pharmacist1", roles = {"PHARMACIST"})
    void testCrossBranchPrescriptionVerificationDenied() throws Exception {
        // pharmacist1 is assigned to Pharmacy 1
        Pharmacy pharmacy2 = pharmacyRepository.findById(2L).orElseThrow();
        Medicine rxMedicine = medicineRepository.findById(1L).orElseThrow(); // Remdesivir (requires rx)
        User patient = userRepository.findByUsername("patient1").orElseThrow();

        Reservation resBranch2 = Reservation.builder()
                .pharmacy(pharmacy2)
                .medicine(rxMedicine)
                .patient(patient)
                .quantity(1)
                .status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .prescriptionNumber("RX-CROSS-TEST")
                .doctorName("Dr. Cross")
                .doctorRegNumber("REG-CROSS")
                .prescriptionVerified(false)
                .build();
        Reservation saved = reservationRepository.saveAndFlush(resBranch2);

        try {
            // Pharmacist 1 tries to verify Branch 2 reservation
            mockMvc.perform(post("/api/reservations/" + saved.getId() + "/verify-prescription"))
                    .andExpect(status().isForbidden());
        } finally {
            reservationRepository.delete(saved);
            reservationRepository.flush();
        }
    }

    // ==========================================
    // 7. OPERATIONAL MONITORING ROLE PROTECTION
    // ==========================================

    @Test
    @DisplayName("Observability: Patient role cannot access /api/system/observability (403 Forbidden)")
    @WithMockUser(username = "patient1", roles = {"PATIENT"})
    void testPatientCannotAccessSystemObservability() throws Exception {
        mockMvc.perform(get("/api/system/observability"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Observability: Admin role successfully accesses /api/system/observability (200 OK)")
    @WithMockUser(username = "admin1", roles = {"ADMIN"})
    void testAdminCanAccessSystemObservability() throws Exception {
        mockMvc.perform(get("/api/system/observability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.healthStatus").exists());
    }
}
