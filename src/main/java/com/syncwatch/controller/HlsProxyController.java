package com.syncwatch.controller;

import com.syncwatch.service.HlsSegmentCache;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/hls")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class HlsProxyController {

    private static final Logger log = LoggerFactory.getLogger(HlsProxyController.class);

    private final HlsSegmentCache cache;

    /**
     * Fetch and rewrite an m3u8 playlist.
     * All segment and nested playlist URLs are rewritten to go through this proxy.
     */
    @GetMapping("/manifest")
    public ResponseEntity<String> manifest(
            @RequestParam String url,
            @RequestHeader(value = "Host", required = false) String host
    ) throws Exception {
        long start = System.currentTimeMillis();
        String content = cache.fetchText(url);
        String baseUrl  = baseUrl(url);
        String rewritten = rewriteManifest(content, url, baseUrl);
        log.info("Manifest fetched and rewritten in {}ms: {}",
                System.currentTimeMillis() - start, shorten(url));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(rewritten);
    }

    /**
     * Serve a cached HLS segment, downloading and prefetching if needed.
     */
    @GetMapping("/segment")
    public ResponseEntity<InputStreamResource> segment(
            @RequestParam String url,
            @RequestParam(required = false) String manifest
    ) throws Exception {
        boolean wasCached = cache.isCached(url);
        long start = System.currentTimeMillis();
        Path path = cache.getSegment(url, manifest);
        long size = cache.segmentSize(path);
        InputStream is = cache.openSegment(path);

        log.info("Segment {} ({} KB){}: {}",
                wasCached ? "HIT" : "MISS",
                size / 1024,
                wasCached ? "" : " downloaded in " + (System.currentTimeMillis() - start) + "ms",
                shorten(url));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "video/mp2t")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(size))
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .body(new InputStreamResource(is));
    }

    /**
     * Prefetch segments starting from the given playback time.
     * Called by the frontend when a remote play/seek event is applied.
     */
    @GetMapping("/warmup")
    public ResponseEntity<Void> warmup(
            @RequestParam String manifest,
            @RequestParam double time
    ) {
        log.info("Warmup requested at {}s for manifest: {}", (int) time, shorten(manifest));
        cache.warmup(manifest, time);
        return ResponseEntity.ok().build();
    }

    /**
     * HLS players routinely abort segment requests on seek / quality switch.
     * That surfaces as "Broken pipe" — harmless, so we swallow it quietly.
     */
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public void handleClientAbort() {
        log.debug("Client aborted segment request (seek/buffer) — ignored");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final Pattern EXTINF = Pattern.compile("#EXTINF:([\\.\\d]+)");

    private String rewriteManifest(String content, String manifestUrl, String baseUrl) {
        List<String> segmentUrls = new ArrayList<>();
        List<Double> durations   = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        double nextDuration = 10.0; // default if EXTINF missing

        for (String line : content.split("\n")) {
            String trimmed = line.trim();

            if (trimmed.startsWith("#EXTINF")) {
                // capture duration for the next segment line
                Matcher dm = EXTINF.matcher(trimmed);
                if (dm.find()) nextDuration = Double.parseDouble(dm.group(1));
                sb.append(trimmed).append("\n");
            } else if (trimmed.startsWith("#")) {
                sb.append(rewriteTagUris(trimmed, manifestUrl, baseUrl)).append("\n");
            } else if (!trimmed.isEmpty()) {
                String absolute = resolveUrl(trimmed, baseUrl);
                if (isPlaylist(absolute)) {
                    sb.append(proxyManifestUrl(absolute)).append("\n");
                } else {
                    segmentUrls.add(absolute);
                    durations.add(nextDuration);
                    sb.append(proxySegmentUrl(absolute, manifestUrl)).append("\n");
                }
            } else {
                sb.append("\n");
            }
        }

        if (!segmentUrls.isEmpty()) {
            cache.registerManifest(manifestUrl, segmentUrls, durations);
        }

        return sb.toString();
    }

    private static final Pattern URI_ATTR = Pattern.compile("URI=\"([^\"]+)\"");

    /**
     * Rewrite URI="..." attributes inside HLS tags.
     * EXT-X-MEDIA (audio/subtitles) and EXT-X-I-FRAME-STREAM-INF point to nested
     * .m3u8 playlists → route them through the manifest endpoint; everything else
     * (EXT-X-KEY, EXT-X-MAP init segments) goes through the segment endpoint.
     */
    private String rewriteTagUris(String tag, String manifestUrl, String baseUrl) {
        Matcher m = URI_ATTR.matcher(tag);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String absolute = resolveUrl(m.group(1), baseUrl);
            String replacement = isPlaylist(absolute)
                    ? proxyManifestUrl(absolute)
                    : proxySegmentUrl(absolute, manifestUrl);
            m.appendReplacement(sb, Matcher.quoteReplacement("URI=\"" + replacement + "\""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean isPlaylist(String url) {
        return url.contains(".m3u8");
    }

    private String proxyManifestUrl(String absoluteUrl) {
        return "/api/hls/manifest?url=" + encode(absoluteUrl);
    }

    private String proxySegmentUrl(String absoluteUrl, String manifestUrl) {
        return "/api/hls/segment?url=" + encode(absoluteUrl)
                + (manifestUrl != null ? "&manifest=" + encode(manifestUrl) : "");
    }

    private static String resolveUrl(String href, String base) {
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        try {
            return URI.create(base).resolve(href).toString();
        } catch (Exception e) {
            return base + href;
        }
    }

    private static String baseUrl(String url) {
        int last = url.lastIndexOf('/');
        return last > 0 ? url.substring(0, last + 1) : url;
    }

    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Trim long URLs for cleaner logs — keep last path segment. */
    private static String shorten(String url) {
        int q = url.indexOf('?');
        String noQuery = q > 0 ? url.substring(0, q) : url;
        int last = noQuery.lastIndexOf('/');
        return last > 0 ? "…/" + noQuery.substring(last + 1) : noQuery;
    }
}
