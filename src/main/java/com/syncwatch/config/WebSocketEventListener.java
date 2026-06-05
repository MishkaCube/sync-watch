package com.syncwatch.config;

import com.syncwatch.model.BufferingEvent;
import com.syncwatch.model.ClockEvent;
import com.syncwatch.model.ParticipantEvent;
import com.syncwatch.service.RoomService;
import com.syncwatch.service.RoomService.BufferingResult;
import com.syncwatch.service.ParticipantService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final ParticipantService participantService;
    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();
        if (destination == null || sessionId == null) return;

        if (destination.startsWith("/topic/room.")) {
            String roomId = destination.substring("/topic/room.".length());
            int previous = participantService.getCount(roomId);
            int count = participantService.join(sessionId, roomId);
            broadcastParticipants(roomId, count, previous);
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId == null) return;

        String roomId = participantService.getRoomId(sessionId);
        if (roomId == null) return;

        // clear any buffering this client was holding (it left without buffering-end)
        BufferingResult buf = roomService.bufferingRemove(roomId, sessionId);
        if (buf != null) {
            if (buf.clockChanged()) {
                messagingTemplate.convertAndSend("/topic/room." + roomId, ClockEvent.from(buf.clock()));
            }
            messagingTemplate.convertAndSend("/topic/room." + roomId, new BufferingEvent(buf.count()));
        }

        int previous = participantService.getCount(roomId);
        int count = participantService.leave(sessionId);
        if (count >= 0) {
            broadcastParticipants(roomId, count, previous);
        }
    }

    private void broadcastParticipants(String roomId, int count, int previous) {
        messagingTemplate.convertAndSend("/topic/room." + roomId,
                new ParticipantEvent("participants-update", count, previous));
    }
}
