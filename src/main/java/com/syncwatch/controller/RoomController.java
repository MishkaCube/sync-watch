package com.syncwatch.controller;

import com.syncwatch.model.ClockEvent;
import com.syncwatch.model.Room;
import com.syncwatch.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<Room> createRoom() {
        return ResponseEntity.ok(roomService.createRoom());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Room> getRoom(@PathVariable String id) {
        return roomService.getRoom(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/clock")
    public ResponseEntity<ClockEvent> getClock(@PathVariable String id) {
        return roomService.getClock(id)
                .map(ClockEvent::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
