package com.syncwatch.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Player/sync event types. The wire format (JSON) uses kebab-case strings;
 */
public enum EventType {
    PLAY("play"),
    PAUSE("pause"),
    SEEK("seek"),
    SOURCE_CHANGE("source-change"),
    SOURCE_RESET("source-reset"),
    BUFFERING_START("buffering-start"),
    BUFFERING_END("buffering-end"),
    CHAT("chat"),
    SYNC("sync"),
    READY("ready"),       // client → server: prepared to start at the barrier position
    UNKNOWN("unknown");

    private final String wire;

    EventType(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static EventType from(String value) {
        if (value == null) return UNKNOWN;
        for (EventType t : values()) {
            if (t.wire.equals(value)) return t;
        }
        return UNKNOWN;
    }
}
