package com.syncwatch.model;

import lombok.Data;

import java.time.Instant;

@Data
public class RoomClock {
    private double  position  = 0;
    private boolean playing   = false;
    private Instant updatedAt = Instant.now();

    /** Current expected position accounting for elapsed time. */
    public double computePosition() {
        if (!playing) return position;
        double elapsed = (Instant.now().toEpochMilli() - updatedAt.toEpochMilli()) / 1000.0;
        return position + elapsed;
    }

    public void play(double pos) {
        this.position  = pos;
        this.playing   = true;
        this.updatedAt = Instant.now();
    }

    public void pause(double pos) {
        this.position  = pos;
        this.playing   = false;
        this.updatedAt = Instant.now();
    }

    public void seek(double pos) {
        this.position  = pos;
        this.updatedAt = Instant.now();
    }
}
