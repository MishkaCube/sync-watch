package com.syncwatch.service;

import com.syncwatch.model.LobbyEvent.LobbyUser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks who's in each room and their self-reported connection quality.
 * Entries expire if a client stops sending presence heartbeats (TTL).
 */
@Service
public class PresenceService {

    private static final long TTL_MS = 12_000;

    private record Entry(String quality, long lastSeen) {}

    // roomId -> (senderId -> Entry)
    private final Map<String, Map<String, Entry>> rooms = new ConcurrentHashMap<>();

    /** Record/refresh a client's presence + quality. Returns the live user list. */
    public List<LobbyUser> update(String roomId, String senderId, String quality) {
        Map<String, Entry> room = rooms.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());
        room.put(senderId, new Entry(quality == null ? "normal" : quality, System.currentTimeMillis()));
        return snapshot(room);
    }

    public List<LobbyUser> remove(String roomId, String senderId) {
        Map<String, Entry> room = rooms.get(roomId);
        if (room == null) return List.of();
        room.remove(senderId);
        return snapshot(room);
    }

    private List<LobbyUser> snapshot(Map<String, Entry> room) {
        long now = System.currentTimeMillis();
        List<LobbyUser> users = new ArrayList<>();
        room.entrySet().removeIf(e -> now - e.getValue().lastSeen() > TTL_MS);
        room.forEach((id, e) -> users.add(new LobbyUser(id, e.quality())));
        return users;
    }
}
