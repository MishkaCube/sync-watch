package com.syncwatch.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class HlsSegmentCache {

    private static final Logger log = LoggerFactory.getLogger(HlsSegmentCache.class);
    private static final int PREFETCH_AHEAD = 8;
    private static final int WARMUP_AHEAD   = 12; // prefetch more on explicit seek/join

    private Path cacheDir;
    private final Map<String, Path>         segmentFiles     = new ConcurrentHashMap<>();
    private final Map<String, List<String>> manifestSegments = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> manifestDurations = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Path>> inFlight = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(6);

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @PostConstruct
    public void init() throws IOException {
        cacheDir = Files.createTempDirectory("syncwatch-hls-");
        log.info("HLS cache dir: {}", cacheDir);
    }

    /** Register segment list + per-segment durations. Prefetch the first few. */
    public void registerManifest(String manifestUrl, List<String> segmentUrls, List<Double> durations) {
        manifestSegments.put(manifestUrl, List.copyOf(segmentUrls));
        manifestDurations.put(manifestUrl, List.copyOf(durations));
        // eagerly prefetch first segments
        segmentUrls.stream().limit(PREFETCH_AHEAD).forEach(url -> prefetch(url, null));
    }

    /**
     * Warmup: find which segment corresponds to the given playback time
     * and prefetch WARMUP_AHEAD segments from that position.
     */
    public void warmup(String manifestUrl, double timeSeconds) {
        List<String> segments = manifestSegments.get(manifestUrl);
        List<Double> durations = manifestDurations.get(manifestUrl);
        if (segments == null || segments.isEmpty()) {
            log.debug("Warmup skipped — manifest not registered: {}", manifestUrl);
            return;
        }

        int idx = 0;
        if (durations != null && !durations.isEmpty()) {
            double elapsed = 0;
            for (int i = 0; i < durations.size(); i++) {
                elapsed += durations.get(i);
                if (elapsed > timeSeconds) { idx = i; break; }
            }
        } else {
            // fallback: assume 10s per segment
            idx = Math.max(0, (int)(timeSeconds / 10.0));
        }
        idx = Math.min(idx, segments.size() - 1);

        int from = idx;
        log.info("Warmup at {}s → segment idx {} of {}, prefetching {} ahead",
                (int) timeSeconds, idx, segments.size(), WARMUP_AHEAD);

        segments.subList(from, Math.min(from + WARMUP_AHEAD, segments.size()))
                .forEach(url -> prefetch(url, manifestUrl));
    }

    /** Get segment — from cache or download. Triggers prefetch of next segments. */
    public Path getSegment(String segmentUrl, String manifestUrl) throws Exception {
        Path cached = segmentFiles.get(segmentUrl);
        if (cached != null) {
            triggerPrefetchAfter(segmentUrl, manifestUrl);
            return cached;
        }
        return downloadAndCache(segmentUrl, manifestUrl);
    }

    private static final int    MAX_RETRIES      = 3;
    private static final long   RETRY_BASE_MS    = 1_000;
    private static final long   SEGMENT_TIMEOUT  = 45;  // seconds per attempt

    private Path downloadAndCache(String segmentUrl, String manifestUrl) throws Exception {
        CompletableFuture<Path> existing = inFlight.get(segmentUrl);
        if (existing != null) return existing.get(SEGMENT_TIMEOUT + 5, TimeUnit.SECONDS);

        CompletableFuture<Path> future = CompletableFuture.supplyAsync(() -> {
            try { return downloadWithRetry(segmentUrl); }
            catch (Exception e) { throw new CompletionException(e); }
        }, executor);

        inFlight.put(segmentUrl, future);
        try {
            Path result = future.get(SEGMENT_TIMEOUT * MAX_RETRIES + 10, TimeUnit.SECONDS);
            triggerPrefetchAfter(segmentUrl, manifestUrl);
            return result;
        } finally {
            inFlight.remove(segmentUrl);
        }
    }

    private Path downloadWithRetry(String url) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return download(url);
            } catch (Exception e) {
                lastException = e;
                long delay = RETRY_BASE_MS * (1L << (attempt - 1)); // 1s, 2s, 4s
                log.warn("Segment download attempt {}/{} failed for {}: {} — retrying in {}ms",
                        attempt, MAX_RETRIES, shorten(url), e.getMessage(), delay);
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(delay);
                }
            }
        }
        log.error("Segment download failed after {} attempts: {}", MAX_RETRIES, shorten(url));
        throw lastException;
    }

    private Path download(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(SEGMENT_TIMEOUT))
                .GET()
                .build();
        Path dest = cacheDir.resolve(Integer.toHexString(url.hashCode()) + ".ts");
        http.send(req, HttpResponse.BodyHandlers.ofFile(dest));
        segmentFiles.put(url, dest);
        return dest;
    }

    private static String shorten(String url) {
        int last = url.lastIndexOf('/');
        return last > 0 ? "…/" + url.substring(last + 1) : url;
    }

    private void triggerPrefetchAfter(String segmentUrl, String manifestUrl) {
        if (manifestUrl == null) return;
        List<String> segments = manifestSegments.get(manifestUrl);
        if (segments == null) return;
        int idx = segments.indexOf(segmentUrl);
        if (idx < 0) return;
        segments.subList(Math.min(idx + 1, segments.size()),
                         Math.min(idx + 1 + PREFETCH_AHEAD, segments.size()))
                .forEach(url -> prefetch(url, manifestUrl));
    }

    private void prefetch(String url, String manifestUrl) {
        if (segmentFiles.containsKey(url) || inFlight.containsKey(url)) return;
        CompletableFuture<Path> f = CompletableFuture.supplyAsync(() -> {
            try { return download(url); }
            catch (Exception e) { log.warn("Prefetch failed: {}", e.getMessage()); return null; }
        }, executor);
        inFlight.put(url, f);
        f.whenComplete((r, e) -> inFlight.remove(url));
    }

    public boolean isCached(String url) { return segmentFiles.containsKey(url); }

    public String fetchText(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    public InputStream openSegment(Path path) throws IOException { return Files.newInputStream(path); }
    public long segmentSize(Path path) throws IOException { return Files.size(path); }
}
