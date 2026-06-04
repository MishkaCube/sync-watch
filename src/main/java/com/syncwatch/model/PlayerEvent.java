package com.syncwatch.model;

import lombok.Data;

@Data
public class PlayerEvent {
    // play | pause | seek | source-change | sync
    private String type;
    private double currentTime;
    private String senderId;
    // for source-change
    private String sourceType; // "youtube" | "url" | "file"
    private String sourceValue;
}
