package com.syncwatch.service;

import com.syncwatch.model.EventType;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.model.Room;
import com.syncwatch.model.RoomClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final SourceCacheService sourceCache;

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public Room createRoom() {
        Room room = new Room();
        room.setId(UUID.randomUUID().toString().substring(0, 8));
        room.setCreatedAt(Instant.now());
        room.setParticipantCount(0);
        rooms.put(room.getId(), room);
        return room;
    }

    public Optional<Room> getRoom(String id) {
        Room room = rooms.get(id);
        if (room == null) return Optional.empty();
        // populate source from Caffeine cache so the response is up to date
        room.setLastSource(sourceCache.get(id).orElse(null));
        return Optional.of(room);
    }

    // ── Clock operations ──────────────────────────────────────────────────────

    /** Get the room, lazily recreating it if it was lost (e.g. after a restart). */
    private Room getOrCreate(String roomId) {
        Room room = rooms.computeIfAbsent(roomId, id -> {
            Room r = new Room();
            r.setId(id);
            r.setCreatedAt(Instant.now());
            r.setParticipantCount(0);
            return r;
        });
        room.setLastActivity(Instant.now());
        return room;
    }

    /** Refresh a room's activity timestamp (called on every WS event). */
    public void touch(String roomId) {
        getOrCreate(roomId);
    }

    // ── Cleanup support ───────────────────────────────────────────────────────

    public java.util.Set<String> roomIds() {
        return new java.util.HashSet<>(rooms.keySet());
    }

    public Instant lastActivity(String roomId) {
        Room room = rooms.get(roomId);
        return room != null ? room.getLastActivity() : null;
    }

    public void remove(String roomId) {
        rooms.remove(roomId);
    }

    public RoomClock clockPlay(String roomId, double position) {
        Room room = getOrCreate(roomId);
        room.getClock().play(position);
        return room.getClock();
    }

    public RoomClock clockPause(String roomId, double position) {
        Room room = getOrCreate(roomId);
        room.getClock().pause(position);
        return room.getClock();
    }

    public RoomClock clockSeek(String roomId, double position) {
        Room room = getOrCreate(roomId);
        room.getClock().seek(position);
        return room.getClock();
    }

    public Optional<RoomClock> getClock(String roomId) {
        return getRoom(roomId).map(Room::getClock);
    }

    // ── Buffering coordination ────────────────────────────────────────────────

    /** Returns buffering result: clock (possibly frozen) + current buffering count. */
    public record BufferingResult(RoomClock clock, int count, boolean clockChanged) {}

    public BufferingResult bufferingStart(String roomId, String senderId) {
        Room room = getOrCreate(roomId);
        boolean wasEmpty = room.getBufferingClients().isEmpty();
        room.getBufferingClients().add(senderId);
        boolean clockChanged = false;
        if (wasEmpty && room.getClock().isPlaying()) {
            // freeze the clock at the current expected position
            room.setPlayingBeforeBuffer(true);
            room.getClock().pause(room.getClock().computePosition());
            clockChanged = true;
        }
        return new BufferingResult(room.getClock(), room.getBufferingClients().size(), clockChanged);
    }

    public BufferingResult bufferingEnd(String roomId, String key) {
        Room room = getOrCreate(roomId);
        room.getBufferingClients().remove(key);
        boolean clockChanged = false;
        if (room.getBufferingClients().isEmpty() && room.isPlayingBeforeBuffer()) {
            // all clients recovered — resume from frozen position
            room.setPlayingBeforeBuffer(false);
            room.getClock().play(room.getClock().getPosition());
            clockChanged = true;
        }
        return new BufferingResult(room.getClock(), room.getBufferingClients().size(), clockChanged);
    }

    /** Clean up buffering when a client disconnects. Returns null if it wasn't buffering. */
    public BufferingResult bufferingRemove(String roomId, String key) {
        Room room = rooms.get(roomId);
        if (room == null) return null;
        if (!room.getBufferingClients().remove(key)) return null;   // wasn't buffering
        boolean clockChanged = false;
        if (room.getBufferingClients().isEmpty() && room.isPlayingBeforeBuffer()) {
            room.setPlayingBeforeBuffer(false);
            room.getClock().play(room.getClock().getPosition());
            clockChanged = true;
        }
        return new BufferingResult(room.getClock(), room.getBufferingClients().size(), clockChanged);
    }

    // ── Source tracking (for late joiners) ────────────────────────────────────

    public void updateLastSource(String roomId, PlayerEvent event) {
        if (event.getType() == EventType.SOURCE_CHANGE) {
            sourceCache.put(roomId, event);
        }
    }

    /** Clear the source and reset the playback clock to the start (paused). */
    public RoomClock resetSource(String roomId) {
        sourceCache.clear(roomId);
        Room room = getOrCreate(roomId);
        room.getClock().pause(0);
        room.getBufferingClients().clear();
        room.setPlayingBeforeBuffer(false);
        return room.getClock();
    }
}
