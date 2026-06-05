package com.syncwatch.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Periodically removes idle rooms: no participants connected and no activity
 * for longer than the configured TTL. Keeps the in-memory room map from growing
 * unbounded over time.
 */
@Component
@RequiredArgsConstructor
public class RoomCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RoomCleanupScheduler.class);

    private final RoomService roomService;
    private final ParticipantService participantService;

    /** Idle TTL in minutes — a room must be empty AND silent this long to be swept. */
    @Value("${syncwatch.room.idle-ttl-minutes:60}")
    private long idleTtlMinutes;

    /** Runs every 15 minutes (configurable). */
    @Scheduled(fixedRateString = "${syncwatch.room.cleanup-interval-ms:900000}")
    public void cleanupIdleRooms() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(idleTtlMinutes));
        int removed = 0;

        for (String roomId : roomService.roomIds()) {
            int participants = participantService.getCount(roomId);
            Instant last = roomService.lastActivity(roomId);

            boolean empty = participants <= 0;
            boolean idle  = last == null || last.isBefore(cutoff);

            if (empty && idle) {
                roomService.remove(roomId);
                participantService.removeRoom(roomId);
                removed++;
            }
        }

        if (removed > 0) {
            log.info("Room cleanup: removed {} idle room(s)", removed);
        }
    }
}
