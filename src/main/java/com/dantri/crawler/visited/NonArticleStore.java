package com.dantri.crawler.visited;

import com.dantri.crawler.config.CrawlerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class NonArticleStore {

    private static final Path FILE = Path.of("data/non_article_urls.jsonl");
    private final CrawlerProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Long> map = new ConcurrentHashMap<>();

    private long ttlMs() { return props.getSettings().getNonArticleTtl(); }

    @PostConstruct
    void load() {
        if (!Files.exists(FILE)) return;
        try (Stream<String> lines = Files.lines(FILE, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                try {
                    var n = mapper.readTree(line);
                    map.put(n.get("url").asText(),
                            Instant.parse(n.get("skippedAt").asText()).toEpochMilli());
                } catch (Exception ex) {
                    log.warn("Skip invalid non-article line: {}", line);
                }
            });
            log.info("Loaded {} non-article URLs", map.size());
        } catch (IOException e) {
            log.error("Error reading {}", FILE, e);
        }
    }

    public synchronized boolean isNonArticle(String url) {
        Long t = map.get(url);
        if (t == null) return false;
        if (System.currentTimeMillis() - t >= ttlMs()) {
            map.remove(url);
            rewriteStore();
            return false;
        }
        return true;
    }

    public synchronized void markNonArticle(String url) {
        if (props.getStartUrls().contains(url)) return;
        long now = System.currentTimeMillis();
        map.put(url, now);
        appendLine(url, now);
    }

    @Scheduled(fixedRate = 60_000)
    void cleanupExpired() {
        long now = System.currentTimeMillis();
        boolean changed = map.entrySet().removeIf(e -> now - e.getValue() >= ttlMs());
        if (changed) rewriteStore();
    }

    private void appendLine(String url, long ts) {
        try {
            Files.createDirectories(FILE.getParent());
            ObjectNode n = mapper.createObjectNode();
            n.put("url", url);
            n.put("skippedAt", Instant.ofEpochMilli(ts).toString());
            Files.writeString(FILE, mapper.writeValueAsString(n) + "\n",
                    StandardCharsets.UTF_8,
                    Files.exists(FILE) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
        } catch (IOException e) {
            log.error("Failed to append non-article {}", url, e);
        }
    }

    private void rewriteStore() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.write(
                    FILE,
                    map.entrySet().stream()
                            .map(e -> {
                                ObjectNode n = mapper.createObjectNode();
                                n.put("url", e.getKey());
                                n.put("skippedAt", Instant.ofEpochMilli(e.getValue()).toString());
                                return n.toString();
                            })
                            .map(s -> s + "\n")
                            .toList(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            log.info("Rewrote non-article store, size={}", map.size());
        } catch (IOException e) {
            log.error("Failed to rewrite non-article store", e);
        }
    }

    public long count() {
        return map.size();
    }

    public List<String> listAll() {
        return new ArrayList<>(map.keySet());
    }
    public synchronized void clearAll() throws IOException {
        map.clear();
        Files.deleteIfExists(FILE);
    }
}
