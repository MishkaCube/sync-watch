package com.syncwatch.controller;

import com.syncwatch.model.ChatMessage;
import com.syncwatch.model.ClockEvent;
import com.syncwatch.model.Room;
import com.syncwatch.service.ChatHistoryService;
import com.syncwatch.service.ParticipantService;
import com.syncwatch.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class RoomController {

    private final RoomService roomService;
    private final ChatHistoryService chatHistory;
    private final ParticipantService participantService;

    @PostMapping
    public ResponseEntity<Room> createRoom() {
        return ResponseEntity.ok(roomService.createRoom());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Room> getRoom(@PathVariable String id) {
        return roomService.getRoom(id)
                .map(room -> {
                    // reflect the live participant count in the response
                    room.setParticipantCount(participantService.getCount(id));
                    return ResponseEntity.ok(room);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/clock")
    public ResponseEntity<ClockEvent> getClock(@PathVariable String id) {
        return roomService.getClock(id)
                .map(ClockEvent::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/chat")
    public ResponseEntity<List<ChatMessage>> getChatHistory(@PathVariable String id) {
        return ResponseEntity.ok(chatHistory.getRecent(id));
    }
}
