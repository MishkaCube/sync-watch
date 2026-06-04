package com.syncwatch.model;

import lombok.Data;

@Data
public class ClockEvent {
    private final String  type      = "clock";
    private double        position;
    private boolean       playing;
    private String        updatedAt;   // ISO-8601

    public static ClockEvent from(RoomClock clock) {
        ClockEvent e = new ClockEvent();
        // Send the CURRENT extrapolated position ("position as of now").
        // The client then adds only the time elapsed since it received this event
        // (tracked locally as receivedAt) — this works for both live broadcasts
        // and REST fetches by a late joiner, and avoids server/client clock skew.
        e.setPosition(clock.computePosition());
        e.setPlaying(clock.isPlaying());
        e.setUpdatedAt(clock.getUpdatedAt().toString());
        return e;
    }
}
