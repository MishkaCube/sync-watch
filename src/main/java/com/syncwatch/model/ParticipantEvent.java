package com.syncwatch.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ParticipantEvent {
    private String type; // "participants-update"
    private int count;
    private int previousCount;
}
