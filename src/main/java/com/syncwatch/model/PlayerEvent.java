package com.syncwatch.model;

import lombok.Data;

@Data
public class PlayerEvent {
    private EventType type;
    private double currentTime;
    private String senderId;
    private String sourceType; // "youtube" | "url" | "file"
    private String sourceValue;
    // for chat
    private String text;
    // for presence
    private String quality;  // "good" | "normal" | "weak"
}
