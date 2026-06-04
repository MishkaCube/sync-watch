package com.syncwatch.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.syncwatch.model.PlayerEvent;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Lightweight Caffeine-backed cache for the current video source per room.
 * Entries expire after a period of inactivity so abandoned rooms don't leak.
 */
@Service
public class SourceCacheService {

    private final Cache<String, PlayerEvent> sources = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(12))
            .maximumSize(10_000)
            .build();

    public void put(String roomId, PlayerEvent source) {
        sources.put(roomId, source);
    }

    public Optional<PlayerEvent> get(String roomId) {
        return Optional.ofNullable(sources.getIfPresent(roomId));
    }

    public void clear(String roomId) {
        sources.invalidate(roomId);
    }
}
