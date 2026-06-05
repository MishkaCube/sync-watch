package com.syncwatch.service;

import com.syncwatch.model.ClockEvent;
import com.syncwatch.model.RoomClock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Coordinates a synchronized playback start (readiness barrier):
 *   1. requestStart  → broadcast "prepare" (everyone seeks + buffers), clock paused
 *   2. markReady     → count readiness against participants
 *   3. all ready (or timeout) → broadcast "go" + set clock playing → everyone starts together
 *
 * This removes the start-time variance caused by one client still buffering.
 */
@Service
@RequiredArgsConstructor
public class PlaybackBarrierService {

    private static final Logger log = LoggerFactory.getLogger(PlaybackBarrierService.class);
    private static final long TIMEOUT_MS = 6000;   // start anyway after this, with whoever's ready

    private final SimpMessagingTemplate broker;
    private final RoomService roomService;
    private final ParticipantService participantService;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "playback-barrier");
        t.setDaemon(true);
        return t;
    });

    private record Pending(double position, Set<String> ready, ScheduledFuture<?> timeout) {}

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    /** Begin a synchronized start at the given position. */
    public synchronized void requestStart(String roomId, double position) {
        cancel(roomId);

        // freeze the clock (paused) at the target so the drift loop stays calm
        RoomClock clock = roomService.clockPause(roomId, position);
        broker.convertAndSend("/topic/room." + roomId, ClockEvent.from(clock));

        // tell everyone to seek + prebuffer
        broker.convertAndSend("/topic/room." + roomId,
                Map.of("type", "prepare", "position", position));

        ScheduledFuture<?> timeout = scheduler.schedule(
                () -> fireGo(roomId), TIMEOUT_MS, TimeUnit.MILLISECONDS);
        pending.put(roomId, new Pending(position, ConcurrentHashMap.newKeySet(), timeout));
        log.info("Room {} barrier: prepare at {}s", roomId, (long) position);
    }

    /** A client reports it's buffered and ready to start. */
    public synchronized void markReady(String roomId, String senderId) {
        Pending p = pending.get(roomId);
        if (p == null) return;
        p.ready().add(senderId);
        int needed = Math.max(1, participantService.getCount(roomId));
        log.info("Room {} barrier: ready {}/{}", roomId, p.ready().size(), needed);
        if (p.ready().size() >= needed) {
            fireGo(roomId);
        }
    }

    /** Cancel any pending barrier (e.g. user paused before it fired). */
    public synchronized void cancel(String roomId) {
        Pending prev = pending.remove(roomId);
        if (prev != null && prev.timeout() != null) prev.timeout().cancel(false);
    }

    private synchronized void fireGo(String roomId) {
        Pending p = pending.remove(roomId);
        if (p == null) return;
        if (p.timeout() != null) p.timeout().cancel(false);

        RoomClock clock = roomService.clockPlay(roomId, p.position());
        // explicit "go" → clients play immediately (already buffered)
        broker.convertAndSend("/topic/room." + roomId,
                Map.of("type", "go", "position", p.position()));
        broker.convertAndSend("/topic/room." + roomId, ClockEvent.from(clock));
        log.info("Room {} barrier: GO at {}s", roomId, (long) p.position());
    }
}
