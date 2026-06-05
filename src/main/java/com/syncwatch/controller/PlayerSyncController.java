package com.syncwatch.controller;

import com.syncwatch.model.BufferingEvent;
import com.syncwatch.model.ChatMessage;
import com.syncwatch.model.ClockEvent;
import com.syncwatch.model.EventType;
import com.syncwatch.model.PlayerEvent;
import com.syncwatch.model.RoomClock;
import com.syncwatch.service.ChatHistoryService;
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
    private final ChatHistoryService chatHistory;

    @MessageMapping("/room/{roomId}/event")
    public void handleEvent(@DestinationVariable String roomId, @Payload PlayerEvent event,
                            org.springframework.messaging.simp.SimpMessageHeaderAccessor headers) {
        EventType type = event.getType();
        roomService.touch(roomId);   // any event keeps the room alive

        switch (type) {
            case PLAY -> {
                RoomClock clock = roomService.clockPlay(roomId, event.getCurrentTime());
                broadcastClock(roomId, clock);
                log.info("Room {} clock PLAY at {}s", roomId, event.getCurrentTime());
            }
            case PAUSE -> {
                RoomClock clock = roomService.clockPause(roomId, event.getCurrentTime());
                broadcastClock(roomId, clock);
                log.info("Room {} clock PAUSE at {}s", roomId, event.getCurrentTime());
            }
            case SEEK -> {
                RoomClock clock = roomService.clockSeek(roomId, event.getCurrentTime());
                broadcastClock(roomId, clock);
                log.info("Room {} clock SEEK to {}s", roomId, event.getCurrentTime());
            }
            case SOURCE_CHANGE -> {
                roomService.updateLastSource(roomId, event);
                messagingTemplate.convertAndSend("/topic/room." + roomId, event);
                log.info("Room {} source changed", roomId);
            }
            case SOURCE_RESET -> {
                RoomClock clock = roomService.resetSource(roomId);
                messagingTemplate.convertAndSend("/topic/room." + roomId, event);
                if (clock != null) broadcastClock(roomId, clock);
                log.info("Room {} source reset", roomId);
            }
            case BUFFERING_START -> {
                BufferingResult r = roomService.bufferingStart(roomId, event.getSenderId());
                if (r != null) {
                    if (r.clockChanged()) broadcastClock(roomId, r.clock());
                    broadcastBuffering(roomId, r.count());
                    log.info("Room {} buffering START — {} client(s) buffering", roomId, r.count());
                }
            }
            case BUFFERING_END -> {
                BufferingResult r = roomService.bufferingEnd(roomId, event.getSenderId());
                if (r != null) {
                    if (r.clockChanged()) broadcastClock(roomId, r.clock());
                    broadcastBuffering(roomId, r.count());
                    log.info("Room {} buffering END — {} client(s) buffering", roomId, r.count());
                }
            }
            case CHAT -> {
                String text = event.getText();
                if (text != null && !text.isBlank()) {
                    // cap length, strip; React escapes on render so no XSS
                    String safe = text.strip();
                    if (safe.length() > 500) safe = safe.substring(0, 500);
                    // identity is bound to the client's IP (server-authoritative, not spoofable)
                    String ipId = headers.getSessionAttributes() != null
                            ? (String) headers.getSessionAttributes().get("ipId")
                            : null;
                    String sender = ipId != null ? ipId : event.getSenderId();
                    ChatMessage msg = new ChatMessage(sender, safe, System.currentTimeMillis());
                    chatHistory.add(roomId, msg);
                    messagingTemplate.convertAndSend("/topic/room." + roomId, msg);
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
