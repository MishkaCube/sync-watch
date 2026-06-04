package com.syncwatch.controller;

import com.syncwatch.model.BufferingEvent;
import com.syncwatch.model.ClockEvent;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.model.RoomClock;
import com.syncwatch.service.RoomService;
import com.syncwatch.service.RoomService.BufferingResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class PlayerSyncController {

    private static final Logger log = LoggerFactory.getLogger(PlayerSyncController.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomService roomService;

    @MessageMapping("/room/{roomId}/event")
    public void handleEvent(@DestinationVariable String roomId, @Payload PlayerEvent event) {
        String type = event.getType();

        switch (type) {
            case "play" -> {
                RoomClock clock = roomService.clockPlay(roomId, event.getCurrentTime());
                broadcastClock(roomId, clock);
                log.info("Room {} clock PLAY at {:.1f}s", roomId, event.getCurrentTime());
            }
            case "pause" -> {
                RoomClock clock = roomService.clockPause(roomId, event.getCurrentTime());
                broadcastClock(roomId, clock);
                log.info("Room {} clock PAUSE at {:.1f}s", roomId, event.getCurrentTime());
            }
            case "seek" -> {
                RoomClock clock = roomService.clockSeek(roomId, event.getCurrentTime());
                broadcastClock(roomId, clock);
                log.info("Room {} clock SEEK to {:.1f}s", roomId, event.getCurrentTime());
            }
            case "source-change" -> {
                roomService.updateLastSource(roomId, event);
                messagingTemplate.convertAndSend("/topic/room." + roomId, event);
                log.info("Room {} source changed", roomId);
            }
            case "source-reset" -> {
                RoomClock clock = roomService.resetSource(roomId);
                // broadcast reset so all clients clear their player
                messagingTemplate.convertAndSend("/topic/room." + roomId, event);
                if (clock != null) broadcastClock(roomId, clock);
                log.info("Room {} source reset", roomId);
            }
            case "buffering-start" -> {
                BufferingResult r = roomService.bufferingStart(roomId, event.getSenderId());
                if (r != null) {
                    if (r.clockChanged()) broadcastClock(roomId, r.clock());
                    broadcastBuffering(roomId, r.count());
                    log.info("Room {} buffering START — {} client(s) buffering", roomId, r.count());
                }
            }
            case "buffering-end" -> {
                BufferingResult r = roomService.bufferingEnd(roomId, event.getSenderId());
                if (r != null) {
                    if (r.clockChanged()) broadcastClock(roomId, r.clock());
                    broadcastBuffering(roomId, r.count());
                    log.info("Room {} buffering END — {} client(s) buffering", roomId, r.count());
                }
            }
            default -> log.debug("Room {} unhandled event type: {}", roomId, type);
        }
    }

    private void broadcastClock(String roomId, RoomClock clock) {
        if (clock == null) return;
        messagingTemplate.convertAndSend("/topic/room." + roomId, ClockEvent.from(clock));
    }

    private void broadcastBuffering(String roomId, int count) {
        messagingTemplate.convertAndSend("/topic/room." + roomId, new BufferingEvent(count));
    }
}
