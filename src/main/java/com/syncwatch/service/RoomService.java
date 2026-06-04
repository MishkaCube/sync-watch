package com.syncwatch.service;

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
        return rooms.computeIfAbsent(roomId, id -> {
            Room room = new Room();
            room.setId(id);
            room.setCreatedAt(Instant.now());
            room.setParticipantCount(0);
            return room;
        });
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

    public BufferingResult bufferingEnd(String roomId, String senderId) {
        Room room = getOrCreate(roomId);
        room.getBufferingClients().remove(senderId);
        boolean clockChanged = false;
        if (room.getBufferingClients().isEmpty() && room.isPlayingBeforeBuffer()) {
            // all clients recovered — resume from frozen position
            room.setPlayingBeforeBuffer(false);
            room.getClock().play(room.getClock().getPosition());
            clockChanged = true;
        }
        return new BufferingResult(room.getClock(), room.getBufferingClients().size(), clockChanged);
    }

    // ── Source tracking (for late joiners) ────────────────────────────────────

    public void updateLastSource(String roomId, PlayerEvent event) {
        if ("source-change".equals(event.getType())) {
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
