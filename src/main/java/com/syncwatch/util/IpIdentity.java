package com.syncwatch.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Derives a stable, non-reversible identity token from a client IP.
 * The raw IP never leaves the server — only this short hash is exposed.
 */
public final class IpIdentity {

    private IpIdentity() {}

    /** Pick the real client IP, honoring nginx's X-Forwarded-For / X-Real-IP. */
    public static String extractIp(String xForwardedFor, String xRealIp, String remoteAddr) {
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // first entry is the original client
            return xForwardedFor.split(",")[0].strip();
        }
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.strip();
        }
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    /** SHA-256 of the IP, first 12 hex chars — stable per IP, not reversible. */
    public static String deriveId(String ip) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (Exception e) {
            return "anon";
        }
    }
}
