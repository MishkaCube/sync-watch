package com.syncwatch.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatMessage {
    private final String type = "chat";
    private String senderId;
    private String text;
    private long   ts;       // server timestamp (epoch millis)
}
