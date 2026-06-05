package com.syncwatch.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.syncwatch.model.ChatMessage;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Keeps the last N chat messages per room in a Caffeine cache.
 * Entries expire 30 minutes after the room goes idle (no reads/writes).
 */
@Service
public class ChatHistoryService {

    private static final int MAX_MESSAGES = 20;

    private final Cache<String, Deque<ChatMessage>> history = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(10_000)
            .build();

    public void add(String roomId, ChatMessage message) {
        Deque<ChatMessage> deque = history.get(roomId, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(message);
            while (deque.size() > MAX_MESSAGES) {
                deque.pollFirst();
            }
        }
    }

    public List<ChatMessage> getRecent(String roomId) {
        Deque<ChatMessage> deque = history.getIfPresent(roomId);
        if (deque == null) return List.of();
        synchronized (deque) {
            return List.copyOf(deque);
        }
    }
}
