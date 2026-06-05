package com.syncwatch.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ParticipantService {

    // sessionId -> roomId
    private final Map<String, String> sessionRoom = new ConcurrentHashMap<>();
    // roomId -> count
    private final Map<String, AtomicInteger> roomCount = new ConcurrentHashMap<>();

    public int join(String sessionId, String roomId) {
        sessionRoom.put(sessionId, roomId);
        return roomCount.computeIfAbsent(roomId, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    public int leave(String sessionId) {
        String roomId = sessionRoom.remove(sessionId);
        if (roomId == null) return -1;
        AtomicInteger count = roomCount.get(roomId);
        if (count == null) return 0;
        return Math.max(0, count.decrementAndGet());
    }

    public String getRoomId(String sessionId) {
        return sessionRoom.get(sessionId);
    }

    public int getCount(String roomId) {
        AtomicInteger count = roomCount.get(roomId);
        return count != null ? count.get() : 0;
    }

    /** Drop all tracking for a room (used by the cleanup scheduler). */
    public void removeRoom(String roomId) {
        roomCount.remove(roomId);
        sessionRoom.values().removeIf(roomId::equals);
    }
}
