package com.syncwatch.model;

import lombok.Data;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class Room {
    private String id;
    private Instant createdAt;
    // refreshed on every event — used by the cleanup scheduler
    private Instant lastActivity = Instant.now();
    private int participantCount;
    // last known player state for late joiners
    private PlayerEvent lastState;
    // when lastState was recorded — used by clients to calculate elapsed playback time
    private Instant lastStateAt;
    // last source-change event — kept separately so late joiners know what to load
    private PlayerEvent lastSource;
    // authoritative room playback clock
    private RoomClock   clock = new RoomClock();
    // clients currently buffering (by senderId)
    private Set<String> bufferingClients = ConcurrentHashMap.newKeySet();
    // whether the clock was playing before buffering froze it
    private boolean     playingBeforeBuffer = false;
}
