package com.syncwatch.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LobbyEvent {
    private final String type = "lobby";
    private List<LobbyUser> users;

    @Data
    @AllArgsConstructor
    public static class LobbyUser {
        private String id;        // client senderId
        private String quality;   // "good" | "normal" | "weak"
    }
}
