package com.sentragrid.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Resolves the genuine client IP address using secure reverse-proxy chain traversal.
 *
 * <p><strong>Security Model:</strong></p>
 * <ul>
 *   <li>Forwarded headers are IGNORED unless the direct peer connection (remoteAddr)
 *       originates from an explicitly configured trusted proxy or trusted CIDR subnet.</li>
 *   <li>When the direct peer is trusted, the X-Forwarded-For chain is traversed from
 *       right to left (from most recent proxy upstream). The first untrusted IP encountered
 *       from the right is the genuine client IP.</li>
 *   <li>Malformed or unparseable IP entries abort chain traversal and fall back safely.</li>
 *   <li>Does NOT trust 0.0.0.0/0 or arbitrary forwarded headers.</li>
 * </ul>
 */
@Slf4j
@Component
public class ClientIpResolver {

    @Value("${app.security.trusted-proxies:}")
    private String trustedProxiesConfig;

    private final List<IpAddressMatcher> trustedProxyMatchers = new ArrayList<>();

    @PostConstruct
    public void init() {
        configureTrustedProxies(this.trustedProxiesConfig);
    }

    public synchronized void configureTrustedProxies(String proxiesConfig) {
        this.trustedProxiesConfig = proxiesConfig;
        this.trustedProxyMatchers.clear();
        if (proxiesConfig != null && !proxiesConfig.isBlank()) {
            Arrays.stream(proxiesConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(entry -> {
                        try {
                            this.trustedProxyMatchers.add(new IpAddressMatcher(entry));
                        } catch (IllegalArgumentException ex) {
                            log.error("Invalid trusted proxy IP or CIDR definition: {}", entry, ex);
                        }
                    });
        }
    }

    public String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String remoteAddr = request.getRemoteAddr() != null ? request.getRemoteAddr().trim() : "127.0.0.1";

        // Forwarded headers are ONLY evaluated if the direct peer is trusted
        if (isTrustedProxy(remoteAddr)) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String[] rawIps = xForwardedFor.split(",");
                // Walk right-to-left: first untrusted IP from the right is the client IP
                for (int i = rawIps.length - 1; i >= 0; i--) {
                    String candidate = rawIps[i].trim();
                    if (!isValidIpAddress(candidate)) {
                        log.warn("Malformed IP '{}' detected in X-Forwarded-For header; aborting chain traversal", candidate);
                        return remoteAddr;
                    }
                    if (!isTrustedProxy(candidate)) {
                        return candidate;
                    }
                }
                // If all IPs in the header are trusted proxies, the leftmost valid IP is used
                String leftmost = rawIps[0].trim();
                if (isValidIpAddress(leftmost)) {
                    return leftmost;
                }
            }
        }
        return remoteAddr;
    }

    public boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank() || trustedProxyMatchers.isEmpty()) {
            return false;
        }
        String trimmed = ip.trim();
        for (IpAddressMatcher matcher : trustedProxyMatchers) {
            try {
                if (matcher.matches(trimmed)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        try {
            new IpAddressMatcher(ip.trim());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}

