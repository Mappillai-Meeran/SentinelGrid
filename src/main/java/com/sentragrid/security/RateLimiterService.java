package com.sentragrid.security;

import com.sentragrid.exception.RateLimitExceededException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory brute-force and rate-limiting service for authentication endpoints.
 *
 * <p><strong>Architectural Limitations & Scope:</strong></p>
 * <ul>
 *   <li>Enforcement is in-memory and local to this specific JVM instance (per-instance, not distributed).</li>
 *   <li>Counters and lockout windows reset upon application restart.</li>
 *   <li>In a clustered or multi-instance deployment behind a load balancer without sticky sessions,
 *       rate limits are evaluated per-instance rather than globally across nodes. A distributed store
 *       such as Redis would be required for global cluster-wide synchronization.</li>
 *   <li>Memory footprint is bounded via explicit maximum capacity limits and proactive eviction of expired
 *       trackers while strictly protecting active lockouts from premature eviction.</li>
 * </ul>
 */
@Slf4j
@Service
public class RateLimiterService {

    public static final int MAX_FAILED_LOGINS = 5;
    public static final long LOGIN_LOCKOUT_SECONDS = 300L; // 5 minutes

    public static final int MAX_REGISTRATIONS_PER_WINDOW = 10;
    public static final long REGISTRATION_WINDOW_SECONDS = 3600L; // 1 hour

    public static final int MAX_AI_SEARCHES_PER_WINDOW = 15;
    public static final long AI_SEARCH_WINDOW_SECONDS = 60L; // 1 minute
    public static final int MAX_AI_SEARCH_TRACKERS = 5_000;

    // Maximum in-memory tracker capacity bounds to prevent heap exhaustion
    public static final int MAX_LOGIN_TRACKERS = 10_000;
    public static final int MAX_REGISTRATION_TRACKERS = 5_000;

    private int maxLoginTrackers = MAX_LOGIN_TRACKERS;
    private int maxRegistrationTrackers = MAX_REGISTRATION_TRACKERS;

    private final Object loginLock = new Object();
    private final Object registrationLock = new Object();

    // Failed login trackers: key -> FailedAttemptTracker
    private final Map<String, FailedAttemptTracker> loginAttemptTrackers = new ConcurrentHashMap<>();

    // Registration trackers: ip -> list of timestamps
    private final Map<String, List<Long>> registrationTrackers = new ConcurrentHashMap<>();

    // AI search trackers: ip -> list of timestamps
    private final Map<String, List<Long>> aiSearchTrackers = new ConcurrentHashMap<>();

    public void setMaxLoginTrackers(int max) {
        this.maxLoginTrackers = max;
    }

    public void setMaxRegistrationTrackers(int max) {
        this.maxRegistrationTrackers = max;
    }

    /**
     * Checks whether a login attempt is permitted for the given username and client IP.
     * Throws {@link RateLimitExceededException} if either the user+IP combination or the IP itself is locked out.
     */
    public void checkLoginAllowed(String username, String clientIp) {
        String userIpKey = buildUserIpKey(username, clientIp);
        checkLockout(userIpKey, "Too many failed login attempts for this user account from your IP.");

        if (clientIp != null && !clientIp.isBlank()) {
            String ipKey = "ip:" + clientIp.trim();
            checkLockout(ipKey, "Too many failed login attempts originating from this IP address.");
        }
    }

    private void checkLockout(String key, String errorPrefix) {
        FailedAttemptTracker tracker = loginAttemptTrackers.get(key);
        if (tracker != null) {
            long now = Instant.now().getEpochSecond();
            long lockExpiresAt = tracker.getLockoutUntilEpochSecond();
            if (lockExpiresAt > now) {
                long retryAfter = lockExpiresAt - now;
                throw new RateLimitExceededException(
                        errorPrefix + " Please try again in " + retryAfter + " seconds.",
                        retryAfter
                );
            }
        }
    }

    /**
     * Records a failed login attempt. If the failure count reaches the threshold, sets a lockout.
     */
    public void recordLoginFailure(String username, String clientIp) {
        String userIpKey = buildUserIpKey(username, clientIp);
        recordFailureForKey(userIpKey);

        if (clientIp != null && !clientIp.isBlank()) {
            String ipKey = "ip:" + clientIp.trim();
            recordFailureForKey(ipKey);
        }
    }

    private void recordFailureForKey(String key) {
        long now = Instant.now().getEpochSecond();

        // Fast path: if key already exists, update atomically without global lock
        FailedAttemptTracker existing = loginAttemptTrackers.get(key);
        if (existing != null) {
            updateExistingTracker(existing, now, key);
            return;
        }

        // Admission path for new key: guarded by loginLock to guarantee global capacity under concurrency
        synchronized (loginLock) {
            FailedAttemptTracker doubleCheck = loginAttemptTrackers.get(key);
            if (doubleCheck != null) {
                updateExistingTracker(doubleCheck, now, key);
                return;
            }

            // Proactive cleanup when approaching capacity
            if (loginAttemptTrackers.size() >= (maxLoginTrackers * 3 / 4)) {
                cleanupExpiredLoginTrackers(now);
            }

            // If at or exceeding capacity, cleanup and evict non-locked entries
            if (loginAttemptTrackers.size() >= maxLoginTrackers) {
                cleanupExpiredLoginTrackers(now);
            }

            if (loginAttemptTrackers.size() >= maxLoginTrackers) {
                evictOldestNonLockedLoginTracker(now);
            }

            // If STILL at capacity (meaning ALL entries are actively locked out!), fail-closed
            if (loginAttemptTrackers.size() >= maxLoginTrackers) {
                log.error("Authentication rate limiter capacity reached ({}/{}). Fail-closed to protect active lockouts and memory.",
                        loginAttemptTrackers.size(), maxLoginTrackers);
                throw new RateLimitExceededException(
                        "Authentication capacity exceeded under extreme load. Please retry in 60 seconds.",
                        60L
                );
            }

            FailedAttemptTracker newTracker = new FailedAttemptTracker(1, 0L, now);
            if (1 >= MAX_FAILED_LOGINS) {
                newTracker.lockoutUntilEpochSecond = now + LOGIN_LOCKOUT_SECONDS;
            }
            loginAttemptTrackers.put(key, newTracker);
        }
    }

    private void updateExistingTracker(FailedAttemptTracker tracker, long now, String key) {
        if (tracker.isLockoutExpired(now)) {
            tracker.failureCount.set(1);
            tracker.lockoutUntilEpochSecond = 0L;
            tracker.lastAttemptEpochSecond = now;
        } else {
            int newCount = tracker.failureCount.incrementAndGet();
            tracker.lastAttemptEpochSecond = now;
            if (newCount >= MAX_FAILED_LOGINS) {
                long lockoutUntil = now + LOGIN_LOCKOUT_SECONDS;
                log.warn("Rate limit breached for key [{}]. Locked out until epoch second {}", key, lockoutUntil);
                tracker.lockoutUntilEpochSecond = lockoutUntil;
            }
        }
    }

    private void evictOldestNonLockedLoginTracker(long now) {
        loginAttemptTrackers.entrySet().stream()
                .filter(e -> !e.getValue().isActivelyLockedOut(now))
                .min(Comparator.comparingLong(e -> e.getValue().getLastAttemptEpochSecond()))
                .ifPresent(oldest -> loginAttemptTrackers.remove(oldest.getKey()));
    }

    /**
     * Clears failed login attempts upon a successful login.
     */
    public void recordLoginSuccess(String username, String clientIp) {
        String userIpKey = buildUserIpKey(username, clientIp);
        loginAttemptTrackers.remove(userIpKey);
        // Note: We deliberately do not clear the overall IP tracker to prevent credential stuffing across distinct usernames.
    }

    /**
     * Checks whether registration is allowed from the given client IP.
     */
    public void checkRegistrationAllowed(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        String ip = clientIp.trim();
        long now = Instant.now().getEpochSecond();
        long cutoff = now - REGISTRATION_WINDOW_SECONDS;

        // Hard capacity enforcement: fail-closed if map is full
        if (!registrationTrackers.containsKey(ip) && registrationTrackers.size() >= maxRegistrationTrackers) {
            cleanupExpiredRegistrationTrackers(now);
            if (!registrationTrackers.containsKey(ip) && registrationTrackers.size() >= maxRegistrationTrackers) {
                throw new RateLimitExceededException(
                        "Registration capacity exceeded under extreme load. Please retry in 60 seconds.",
                        60L
                );
            }
        }

        List<Long> attempts = registrationTrackers.get(ip);
        if (attempts != null) {
            synchronized (attempts) {
                attempts.removeIf(timestamp -> timestamp < cutoff);
                if (attempts.size() >= MAX_REGISTRATIONS_PER_WINDOW) {
                    long oldest = attempts.isEmpty() ? now : attempts.get(0);
                    long retryAfter = Math.max(1L, (oldest + REGISTRATION_WINDOW_SECONDS) - now);
                    throw new RateLimitExceededException(
                            "Registration rate limit exceeded for this IP. Please try again in " + retryAfter + " seconds.",
                            retryAfter
                    );
                }
            }
        }
    }

    /**
     * Records a registration attempt for the client IP.
     */
    public void recordRegistration(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        String ip = clientIp.trim();
        long now = Instant.now().getEpochSecond();

        List<Long> existingList = registrationTrackers.get(ip);
        if (existingList != null) {
            existingList.add(now);
            return;
        }

        synchronized (registrationLock) {
            List<Long> doubleCheck = registrationTrackers.get(ip);
            if (doubleCheck != null) {
                doubleCheck.add(now);
                return;
            }

            if (registrationTrackers.size() >= (maxRegistrationTrackers * 3 / 4)) {
                cleanupExpiredRegistrationTrackers(now);
            }

            if (registrationTrackers.size() >= maxRegistrationTrackers) {
                cleanupExpiredRegistrationTrackers(now);
            }

            if (registrationTrackers.size() >= maxRegistrationTrackers) {
                evictOldestNonRateLimitedRegistrationTracker(now);
            }

            if (registrationTrackers.size() >= maxRegistrationTrackers) {
                log.error("Registration rate limiter capacity reached ({}/{}). Fail-closed to protect memory.",
                        registrationTrackers.size(), maxRegistrationTrackers);
                throw new RateLimitExceededException(
                        "Registration capacity exceeded under extreme load. Please retry in 60 seconds.",
                        60L
                );
            }

            List<Long> newList = Collections.synchronizedList(new ArrayList<>());
            newList.add(now);
            registrationTrackers.put(ip, newList);
        }
    }

    private void evictOldestNonRateLimitedRegistrationTracker(long now) {
        long cutoff = now - REGISTRATION_WINDOW_SECONDS;
        registrationTrackers.entrySet().stream()
                .filter(e -> {
                    List<Long> list = e.getValue();
                    synchronized (list) {
                        long activeAttempts = list.stream().filter(ts -> ts >= cutoff).count();
                        return activeAttempts < MAX_REGISTRATIONS_PER_WINDOW;
                    }
                })
                .findFirst()
                .ifPresent(candidate -> registrationTrackers.remove(candidate.getKey()));
    }

    /**
     * Cleans up expired login trackers. Actively locked out entries are strictly protected
     * and NEVER removed until their lockout window has fully elapsed.
     */
    public synchronized void cleanupExpiredLoginTrackers(long now) {
        // 1. Remove entries whose lockout has expired or are idle without active lock
        loginAttemptTrackers.entrySet().removeIf(entry -> entry.getValue().isSafeToEvict(now));

        // 2. If still exceeding maximum bounds, evict oldest non-locked entries
        if (loginAttemptTrackers.size() >= maxLoginTrackers) {
            int toEvict = (loginAttemptTrackers.size() - maxLoginTrackers) + 1;
            loginAttemptTrackers.entrySet().stream()
                    .filter(e -> !e.getValue().isActivelyLockedOut(now))
                    .sorted(Comparator.comparingLong(e -> e.getValue().getLastAttemptEpochSecond()))
                    .limit(toEvict)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(loginAttemptTrackers::remove);
        }
    }

    /**
     * Cleans up expired registration trackers where all attempt timestamps are outside the rolling window.
     */
    public synchronized void cleanupExpiredRegistrationTrackers(long now) {
        long cutoff = now - REGISTRATION_WINDOW_SECONDS;
        registrationTrackers.entrySet().removeIf(entry -> {
            List<Long> attempts = entry.getValue();
            synchronized (attempts) {
                attempts.removeIf(ts -> ts < cutoff);
                return attempts.isEmpty();
            }
        });

        if (registrationTrackers.size() >= maxRegistrationTrackers) {
            evictOldestNonRateLimitedRegistrationTracker(now);
        }
    }

    /**
     * Public method to run comprehensive memory maintenance.
     */
    public void performMaintenanceCleanup() {
        long now = Instant.now().getEpochSecond();
        cleanupExpiredLoginTrackers(now);
        cleanupExpiredRegistrationTrackers(now);
    }

    public int getLoginTrackerCount() {
        return loginAttemptTrackers.size();
    }

    public int getRegistrationTrackerCount() {
        return registrationTrackers.size();
    }

    public int getAiSearchTrackerCount() {
        return aiSearchTrackers.size();
    }

    /**
     * Checks whether an AI search normalization request is permitted for the given client IP.
     * Prevents Gemini API quota exhaustion by limiting each IP to 15 searches per minute.
     */
    public void checkAiSearchAllowed(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        String ip = clientIp.trim();
        long now = Instant.now().getEpochSecond();
        long cutoff = now - AI_SEARCH_WINDOW_SECONDS;

        List<Long> timestamps = aiSearchTrackers.get(ip);
        if (timestamps != null) {
            synchronized (timestamps) {
                timestamps.removeIf(t -> t < cutoff);
                if (timestamps.size() >= MAX_AI_SEARCHES_PER_WINDOW) {
                    long oldest = timestamps.isEmpty() ? now : timestamps.get(0);
                    long retryAfter = Math.max(1, AI_SEARCH_WINDOW_SECONDS - (now - oldest));
                    log.warn("AI search rate limit breached for IP [{}]. Count: {}. Retry-After: {}s",
                            ip, timestamps.size(), retryAfter);
                    throw new RateLimitExceededException(
                            "AI search rate limit exceeded (maximum " + MAX_AI_SEARCHES_PER_WINDOW +
                            " queries per minute per client). Please retry in " + retryAfter + " seconds.",
                            retryAfter
                    );
                }
            }
        }
    }

    /**
     * Records an AI search normalization request for the client IP.
     */
    public void recordAiSearch(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        String ip = clientIp.trim();
        long now = Instant.now().getEpochSecond();
        long cutoff = now - AI_SEARCH_WINDOW_SECONDS;

        List<Long> timestamps = aiSearchTrackers.computeIfAbsent(ip, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (timestamps) {
            timestamps.removeIf(t -> t < cutoff);
            timestamps.add(now);
        }

        if (aiSearchTrackers.size() > MAX_AI_SEARCH_TRACKERS) {
            cleanupExpiredAiSearchTrackers(now);
        }
    }

    public synchronized void cleanupExpiredAiSearchTrackers(long now) {
        long cutoff = now - AI_SEARCH_WINDOW_SECONDS;
        aiSearchTrackers.entrySet().removeIf(entry -> {
            List<Long> list = entry.getValue();
            synchronized (list) {
                list.removeIf(t -> t < cutoff);
                return list.isEmpty();
            }
        });
    }

    /**
     * Clears all rate limiting states (primarily for testing).
     */
    public void reset() {
        this.maxLoginTrackers = MAX_LOGIN_TRACKERS;
        this.maxRegistrationTrackers = MAX_REGISTRATION_TRACKERS;
        loginAttemptTrackers.clear();
        registrationTrackers.clear();
        aiSearchTrackers.clear();
    }

    private String buildUserIpKey(String username, String clientIp) {
        String u = username != null ? username.trim().toLowerCase() : "anonymous";
        String ip = clientIp != null ? clientIp.trim() : "unknown";
        return u + "@" + ip;
    }

    @Getter
    private static class FailedAttemptTracker {
        private final AtomicInteger failureCount;
        private volatile long lockoutUntilEpochSecond;
        private volatile long lastAttemptEpochSecond;

        FailedAttemptTracker(int initialCount, long lockoutUntil, long lastAttempt) {
            this.failureCount = new AtomicInteger(initialCount);
            this.lockoutUntilEpochSecond = lockoutUntil;
            this.lastAttemptEpochSecond = lastAttempt;
        }

        boolean isLockoutExpired(long now) {
            if (lockoutUntilEpochSecond > 0 && lockoutUntilEpochSecond < now) {
                return true;
            }
            // Expire after 10 minutes of inactivity if not locked out
            return lockoutUntilEpochSecond == 0 && (now - lastAttemptEpochSecond > 600L);
        }

        boolean isActivelyLockedOut(long now) {
            return lockoutUntilEpochSecond > now;
        }

        boolean isSafeToEvict(long now) {
            // Actively locked out entries MUST NEVER be evicted prematurely
            if (isActivelyLockedOut(now)) {
                return false;
            }
            // If it had a lockout that has expired, or is inactive for > 10 minutes
            return (lockoutUntilEpochSecond > 0 && lockoutUntilEpochSecond <= now)
                    || (now - lastAttemptEpochSecond > 600L);
        }
    }
}

