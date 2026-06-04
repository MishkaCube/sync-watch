package com.syncwatch.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BufferingEvent {
    private final String type = "buffering";
    private int count;   // how many clients are currently buffering
}
