package com.syncwatch.controller;

import com.syncwatch.util.IpIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Returns the caller's IP-derived identity so the client knows "who am I"
 * for chat (detecting own messages + stable avatar). Uses the same derivation
 * as the WebSocket handshake, so the id matches the one stamped on messages.
 */
@RestController
@RequestMapping("/api/whoami")
@CrossOrigin(origins = "http://localhost:5173")
public class WhoAmIController {

    @GetMapping
    public ResponseEntity<Map<String, String>> whoami(HttpServletRequest request) {
        String ip = IpIdentity.extractIp(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr());
        return ResponseEntity.ok(Map.of("id", IpIdentity.deriveId(ip)));
    }
}
