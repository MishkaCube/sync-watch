package com.syncwatch.config;

import com.syncwatch.util.IpIdentity;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Captures the client's IP at WebSocket handshake time and stores its derived
 * identity in the STOMP session attributes (key "ipId"), so chat messages can
 * be stamped with a stable, server-authoritative sender id.
 */
public class IpHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String ip = "unknown";
        if (request instanceof ServletServerHttpRequest servletRequest) {
            var req = servletRequest.getServletRequest();
            ip = IpIdentity.extractIp(
                    req.getHeader("X-Forwarded-For"),
                    req.getHeader("X-Real-IP"),
                    req.getRemoteAddr());
        }
        attributes.put("ipId", IpIdentity.deriveId(ip));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
